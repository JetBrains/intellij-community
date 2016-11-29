package com.intellij.structuralsearch;

import com.intellij.dupLocator.PsiElementRole;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptor;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptorProvider;
import com.intellij.dupLocator.equivalence.MultiChildDescriptor;
import com.intellij.dupLocator.equivalence.SingleChildDescriptor;
import com.intellij.dupLocator.iterators.FilteringNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.*;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class StructuralSearchProfileBase extends StructuralSearchProfile {
  private static final String DELIMETER_CHARS = ",;.[]{}():";
  protected static final String PATTERN_PLACEHOLDER = "$$PATTERN_PLACEHOLDER$$";
  private PsiElementVisitor myLexicalNodesFilter;

  @Override
  public void compile(PsiElement[] elements, @NotNull final GlobalCompilingVisitor globalVisitor) {
    final PsiElement topElement = elements[0].getParent();
    final PsiElement element = elements.length > 1 ? topElement : elements[0];

    element.accept(new MyCompilingVisitor(globalVisitor, topElement));

    element.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        if (DuplocatorUtil.isIgnoredNode(element)) {
          return;
        }
        CompiledPattern pattern = globalVisitor.getContext().getPattern();
        MatchingHandler handler = pattern.getHandler(element);

        if (!(handler instanceof SubstitutionHandler) &&
            !(handler instanceof TopLevelMatchingHandler) &&
            !(handler instanceof LightTopLevelMatchingHandler)) {
          pattern.setHandler(element, new SkippingHandler(handler));
        }

        // todo: simplify logic

        /*
        place skipping handler under top-level handler, because when we skip top-level node we can get non top-level handler, so
        depth matching won't be done!;
         */
        if (handler instanceof LightTopLevelMatchingHandler) {
          MatchingHandler delegate = ((LightTopLevelMatchingHandler)handler).getDelegate();
          if (!(delegate instanceof SubstitutionHandler)) {
            pattern.setHandler(element, new LightTopLevelMatchingHandler(new SkippingHandler(delegate)));
          }
        }
      }
    });


    final Language baseLanguage = element.getContainingFile().getLanguage();

    // todo: try to optimize it: too heavy strategy!
    globalVisitor.getContext().getPattern().setStrategy(new MatchingStrategy() {
      @Override
      public boolean continueMatching(PsiElement start) {
        Language language = start.getLanguage();

        PsiFile file = start.getContainingFile();
        if (file != null) {
          Language fileLanguage = file.getLanguage();
          if (fileLanguage.isKindOf(language)) {
            // dialect
            language = fileLanguage;
          }
        }

        return language == baseLanguage;
      }

      @Override
      public boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
        return DuplocatorUtil.shouldSkip(element, elementToMatchWith);
      }
    });
  }

  @NotNull
  @Override
  public PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new MyMatchingVisitor(globalVisitor);
  }

  @NotNull
  @Override
  public PsiElementVisitor getLexicalNodesFilter(@NotNull final LexicalNodesFilter filter) {
    if (myLexicalNodesFilter == null) {
      myLexicalNodesFilter = new PsiElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          super.visitElement(element);
          if (DuplocatorUtil.isIgnoredNode(element)) {
            filter.setResult(true);
          }
        }
      };
    }
    return myLexicalNodesFilter;
  }

  public static boolean containsOnlyDelimeters(String s) {
    for (int i = 0, n = s.length(); i < n; i++) {
      if (DELIMETER_CHARS.indexOf(s.charAt(i)) < 0) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  protected abstract String[] getVarPrefixes();

  @NotNull
  @Override
  public CompiledPattern createCompiledPattern() {
    return new CompiledPattern() {

      @Override
      protected SubstitutionHandler doCreateSubstitutionHandler(String name, boolean target, int minOccurs, int maxOccurs, boolean greedy) {
        return new MySubstitutionHandler(name, target, minOccurs, maxOccurs, greedy);
      }

      @Override
      public String[] getTypedVarPrefixes() {
        return getVarPrefixes();
      }

      @Override
      public boolean isTypedVar(String str) {
        for (String prefix : getVarPrefixes()) {
          if (str.startsWith(prefix)) {
            return true;
          }
        }
        return false;
      }

      @NotNull
      @Override
      public String getTypedVarString(PsiElement element) {
        final PsiElement initialElement = element;
        PsiElement child = SkippingHandler.getOnlyNonWhitespaceChild(element);

        while (child != element && child != null && !(child instanceof LeafElement)) {
          element = child;
          child = SkippingHandler.getOnlyNonWhitespaceChild(element);
        }
        return child instanceof LeafElement ? element.getText() : initialElement.getText();
      }
    };
  }

  @Override
  public boolean isMyLanguage(@NotNull Language language) {
    return language.isKindOf(getFileType().getLanguage());
  }

  @NotNull
  protected abstract LanguageFileType getFileType();

  @NotNull
  @Override
  public PsiElement[] createPatternTree(@NotNull String text,
                                        @NotNull PatternTreeContext context,
                                        @NotNull FileType fileType,
                                        @Nullable Language language,
                                        @Nullable String contextName,
                                        @Nullable String extension,
                                        @NotNull Project project,
                                        boolean physical) {
    if (context == PatternTreeContext.Block) {
      final String strContext = getContext(text, language, contextName);
      return strContext != null ?
             parsePattern(project, strContext, text, fileType, language, extension, physical) :
             PsiElement.EMPTY_ARRAY;
    }
    return super.createPatternTree(text, context, fileType, language, contextName, extension, project, physical);
  }

  @Override
  public void checkReplacementPattern(Project project, ReplaceOptions options) {
    final CompiledPattern compiledPattern = PatternCompiler.compilePattern(project, options.getMatchOptions());
    if (compiledPattern == null) {
      return;
    }

    final NodeIterator it = compiledPattern.getNodes();
    if (!it.hasNext()) {
      return;
    }

    final PsiElement root = it.current().getParent();

    if (!checkOptionalChildren(root) ||
        !checkErrorElements(root)) {
      throw new UnsupportedPatternException(": Partial and expression patterns are not supported");
    }
  }

  private static boolean checkErrorElements(PsiElement element) {
    final boolean[] result = {true};
    final int endOffset = element.getTextRange().getEndOffset();

    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);

        if (element instanceof PsiErrorElement && element.getTextRange().getEndOffset() == endOffset) {
          result[0] = false;
        }
      }
    });

    return result[0];
  }

  private static boolean checkOptionalChildren(PsiElement root) {
    final boolean[] result = {true};

    root.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);

        if (element instanceof LeafElement) {
          return;
        }

        final EquivalenceDescriptorProvider provider = EquivalenceDescriptorProvider.getInstance(element);
        if (provider == null) {
          return;
        }

        final EquivalenceDescriptor descriptor = provider.buildDescriptor(element);
        if (descriptor == null) {
          return;
        }

        for (SingleChildDescriptor childDescriptor : descriptor.getSingleChildDescriptors()) {
          if (childDescriptor.getType() == SingleChildDescriptor.MyType.OPTIONALLY_IN_PATTERN &&
              childDescriptor.getElement() == null) {
            result[0] = false;
          }
        }

        for (MultiChildDescriptor childDescriptor : descriptor.getMultiChildDescriptors()) {
          if (childDescriptor.getType() == MultiChildDescriptor.MyType.OPTIONALLY_IN_PATTERN) {
            PsiElement[] elements = childDescriptor.getElements();
            if (elements == null || elements.length == 0) {
              result[0] = false;
            }
          }
        }
      }
    });
    return result[0];
  }

  @Override
  public StructuralReplaceHandler getReplaceHandler(@NotNull ReplacementContext context) {
    return new DocumentBasedReplaceHandler(context.getProject());
  }

  @NotNull
  public String[] getContextNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Nullable
  protected String getContext(@NotNull String pattern, @Nullable Language language, @Nullable String contextName) {
    return PATTERN_PLACEHOLDER;
  }

  private static boolean canBePatternVariable(PsiElement element) {
    // can be leaf element! (ex. var a = 1 <-> var $a$ = 1)
    if (element instanceof LeafElement) {
      return true;
    }

    while (!(element instanceof LeafElement) && element != null) {
      element = SkippingHandler.getOnlyNonWhitespaceChild(element);
    }
    return element != null;
  }

  protected boolean isStringLiteral(PsiElement element) {
    if (element == null) return false;
    final ASTNode astNode = element.getNode();
    if (astNode == null) {
      return false;
    }
    final IElementType elementType = astNode.getElementType();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
    if (parserDefinition != null) {
      final TokenSet literals = parserDefinition.getStringLiteralElements();
      return literals.contains(elementType);
    }
    return false;
  }

  private static boolean canBePatternVariableValue(PsiElement element) {
    // can be leaf element! (ex. var a = 1 <-> var $a$ = 1)
    return !containsOnlyDelimeters(element.getText());
  }

  @Override
  public boolean canBeVarDelimeter(@NotNull PsiElement element) {
    final ASTNode node = element.getNode();
    return node != null && getVariableDelimiters().contains(node.getElementType());
  }

  protected TokenSet getVariableDelimiters() {
    return TokenSet.EMPTY;
  }

  public static PsiElement[] parsePattern(Project project,
                                          String context,
                                          String pattern,
                                          FileType fileType,
                                          Language language,
                                          String extension,
                                          boolean physical) {
    int offset = context.indexOf(PATTERN_PLACEHOLDER);

    final int patternLength = pattern.length();
    final String patternInContext = context.replace(PATTERN_PLACEHOLDER, pattern);

    final String ext = extension != null ? extension : fileType.getDefaultExtension();
    final String name = "__dummy." + ext;
    final PsiFileFactory factory = PsiFileFactory.getInstance(project);

    final PsiFile file = language == null
                         ? factory.createFileFromText(name, fileType, patternInContext, LocalTimeCounter.currentTime(), physical, true)
                         : factory.createFileFromText(name, language, patternInContext, physical, true);
    if (file == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    final List<PsiElement> result = new ArrayList<>();

    PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    PsiElement topElement = element;
    element = element.getParent();

    while (element != null) {
      if (element.getTextRange().getStartOffset() == offset && element.getTextLength() <= patternLength) {
        topElement = element;
      }
      element = element.getParent();
    }

    if (topElement instanceof PsiFile) {
      return topElement.getChildren();
    }

    final int endOffset = offset + patternLength;
    result.add(topElement);
    topElement = topElement.getNextSibling();

    while (topElement != null && topElement.getTextRange().getEndOffset() <= endOffset) {
      result.add(topElement);
      topElement = topElement.getNextSibling();
    }

    return result.toArray(new PsiElement[result.size()]);
  }

  // todo: support expression patterns
  // todo: support {statement;} = statement; (node has only non-lexical child)

  private class MyCompilingVisitor extends PsiRecursiveElementVisitor {
    private final GlobalCompilingVisitor myGlobalVisitor;
    private final PsiElement myTopElement;

    private Pattern[] mySubstitutionPatterns;

    private MyCompilingVisitor(GlobalCompilingVisitor globalVisitor, PsiElement topElement) {
      myGlobalVisitor = globalVisitor;
      myTopElement = topElement;
    }

    @Override
    public void visitElement(PsiElement element) {
      doVisitElement(element);

      if (isStringLiteral(element)) {
        visitLiteral(element);
      }
    }

    private void doVisitElement(PsiElement element) {
      CompiledPattern pattern = myGlobalVisitor.getContext().getPattern();

      if (myGlobalVisitor.getCodeBlockLevel() == 0) {
        initTopLevelElement(element);
        return;
      }

      if (canBePatternVariable(element) && pattern.isRealTypedVar(element)) {
        myGlobalVisitor.handle(element);
        final MatchingHandler handler = pattern.getHandler(element);
        handler.setFilter(new NodeFilter() {
          public boolean accepts(PsiElement other) {
            return canBePatternVariableValue(other);
          }
        });

        super.visitElement(element);

        return;
      }

      super.visitElement(element);

      if (myGlobalVisitor.getContext().getSearchHelper().doOptimizing() && element instanceof LeafElement) {
        ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
        if (parserDefinition != null) {
          String text = element.getText();

          // todo: support variables inside comments
          boolean flag = true;
          if (StringUtil.isJavaIdentifier(text) && flag) {
            myGlobalVisitor.processTokenizedName(text, true, GlobalCompilingVisitor.OccurenceKind.CODE);
          }
        }
      }
    }

    private void visitLiteral(PsiElement literal) {
      String value = literal.getText();

      if (StringUtil.isQuotedString(value)) {
        if (mySubstitutionPatterns == null) {
          final String[] prefixes = myGlobalVisitor.getContext().getPattern().getTypedVarPrefixes();
          mySubstitutionPatterns = createPatterns(prefixes);
        }

        for (Pattern substitutionPattern : mySubstitutionPatterns) {
          @Nullable MatchingHandler handler =
            myGlobalVisitor.processPatternStringWithFragments(value, GlobalCompilingVisitor.OccurenceKind.LITERAL, substitutionPattern);

          if (handler != null) {
            literal.putUserData(CompiledPattern.HANDLER_KEY, handler);
            break;
          }
        }
      }
    }

    private Pattern[] createPatterns(String[] prefixes) {
      final Pattern[] patterns = new Pattern[prefixes.length];

      for (int i = 0; i < prefixes.length; i++) {
        final String s = StructuralSearchUtil.shieldSpecialChars(prefixes[i]);
        patterns[i] = Pattern.compile("\\b(" + s + "\\w+)\\b");
      }
      return patterns;
    }

    private void initTopLevelElement(PsiElement element) {
      CompiledPattern pattern = myGlobalVisitor.getContext().getPattern();

      PsiElement newElement = SkippingHandler.skipNodeIfNeccessary(element);

      if (element != newElement && newElement != null) {
        // way to support partial matching (ex. if ($condition$) )
        newElement.accept(this);
        pattern.setHandler(element, new LightTopLevelMatchingHandler(pattern.getHandler(element)));
      }
      else {
        myGlobalVisitor.setCodeBlockLevel(myGlobalVisitor.getCodeBlockLevel() + 1);

        for (PsiElement el = element.getFirstChild(); el != null; el = el.getNextSibling()) {
          if (GlobalCompilingVisitor.getFilter().accepts(el)) {
            if (el instanceof PsiWhiteSpace) {
              myGlobalVisitor.addLexicalNode(el);
            }
          }
          else {
            el.accept(this);

            MatchingHandler matchingHandler = pattern.getHandler(el);
            pattern.setHandler(el, element == myTopElement ? new TopLevelMatchingHandler(matchingHandler) :
                                   new LightTopLevelMatchingHandler(matchingHandler));

            /*
              do not assign light-top-level handlers through skipping, because it is incorrect;
              src: if (...) { st1; st2; }
              pattern: if (...) {$a$;}

              $a$ will have top-level handler, so matching will be considered as correct, although "st2;" is left!
             */
          }
        }

        myGlobalVisitor.setCodeBlockLevel(myGlobalVisitor.getCodeBlockLevel() - 1);
        pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)));
      }
    }
  }

  private class MyMatchingVisitor extends PsiElementVisitor {
    private final GlobalMatchingVisitor myGlobalVisitor;

    private MyMatchingVisitor(GlobalMatchingVisitor globalVisitor) {
      myGlobalVisitor = globalVisitor;
    }

    private boolean shouldIgnoreVarNode(PsiElement element) {
      MatchingHandler handler = myGlobalVisitor.getMatchContext().getPattern().getHandlerSimple(element);
      if (handler instanceof DelegatingHandler) {
        handler = ((DelegatingHandler)handler).getDelegate();
      }
      return handler instanceof MySubstitutionHandler && ((MySubstitutionHandler)handler).myExceptedNodes.contains(element);
    }

    @Override
    public void visitElement(PsiElement element) {
      super.visitElement(element);

      final EquivalenceDescriptorProvider descriptorProvider = EquivalenceDescriptorProvider.getInstance(element);

      if (descriptorProvider != null) {
        final EquivalenceDescriptor descriptor1 = descriptorProvider.buildDescriptor(element);
        final EquivalenceDescriptor descriptor2 = descriptorProvider.buildDescriptor(myGlobalVisitor.getElement());

        if (descriptor1 != null && descriptor2 != null) {
          final boolean result = DuplocatorUtil
            .match(descriptor1, descriptor2, myGlobalVisitor, Collections.<PsiElementRole>emptySet(), null);
          myGlobalVisitor.setResult(result);
          return;
        }
      }

      if (isStringLiteral(element)) {
        visitLiteral(element);
        return;
      }

      if (canBePatternVariable(element) &&
          myGlobalVisitor.getMatchContext().getPattern().isRealTypedVar(element) &&
          !shouldIgnoreVarNode(element)) {

        PsiElement matchedElement = myGlobalVisitor.getElement();
        PsiElement newElement = SkippingHandler.skipNodeIfNeccessary(matchedElement);
        while (newElement != matchedElement) {
          matchedElement = newElement;
          newElement = SkippingHandler.skipNodeIfNeccessary(matchedElement);
        }

        myGlobalVisitor.setResult(myGlobalVisitor.handleTypedElement(element, matchedElement));
      }
      else if (element instanceof LeafElement) {
        myGlobalVisitor.setResult(element.getText().equals(myGlobalVisitor.getElement().getText()));
      }
      else if (element.getFirstChild() == null && element.getTextLength() == 0) {
        myGlobalVisitor.setResult(true);
      }
      else {
        PsiElement patternChild = element.getFirstChild();
        PsiElement matchedChild = myGlobalVisitor.getElement().getFirstChild();

        FilteringNodeIterator patternIterator = new SsrFilteringNodeIterator(patternChild);
        FilteringNodeIterator matchedIterator = new SsrFilteringNodeIterator(matchedChild);

        boolean matched = myGlobalVisitor.matchSequentially(patternIterator, matchedIterator);
        myGlobalVisitor.setResult(matched);
      }
    }

    private void visitLiteral(PsiElement literal) {
      final PsiElement l2 = myGlobalVisitor.getElement();

      MatchingHandler handler = (MatchingHandler)literal.getUserData(CompiledPattern.HANDLER_KEY);

      if (handler instanceof SubstitutionHandler) {
        int offset = 0;
        int length = l2.getTextLength();
        final String text = l2.getText();

        if (length > 2) {
          final char c = text.charAt(0);
          if ((c == '"' || c == '\'') && text.charAt(length - 1) == c) {
            final boolean looseMatching = myGlobalVisitor.getMatchContext().getOptions().isLooseMatching();
            if (!looseMatching && c != literal.getText().charAt(0)) {
              myGlobalVisitor.setResult(false);
              return;
            }
            length--;
            offset++;
          }
        }
        myGlobalVisitor.setResult(((SubstitutionHandler)handler).handle(l2, offset, length, myGlobalVisitor.getMatchContext()));
      }
      else if (handler != null) {
        myGlobalVisitor.setResult(handler.match(literal, l2, myGlobalVisitor.getMatchContext()));
      }
      else {
        myGlobalVisitor.setResult(literal.textMatches(l2));
      }
    }
  }

  private static class MySubstitutionHandler extends SubstitutionHandler {
    final Set<PsiElement> myExceptedNodes;

    public MySubstitutionHandler(String name, boolean target, int minOccurs, int maxOccurs, boolean greedy) {
      super(name, target, minOccurs, maxOccurs, greedy);
      myExceptedNodes = new HashSet<>();
    }

    @Override
    public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
      if (doMatchSequentially(nodes, nodes2, context)) {
        return true;
      }
      final PsiElement current = nodes.current();
      if (current != null) {
        myExceptedNodes.add(current);
      }
      final boolean result = doMatchSequentiallyBySimpleHandler(nodes, nodes2, context);
      myExceptedNodes.remove(current);
      return result;
    }
  }
}
