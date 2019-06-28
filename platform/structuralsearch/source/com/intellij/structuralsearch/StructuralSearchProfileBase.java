// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.dupLocator.equivalence.EquivalenceDescriptor;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptorProvider;
import com.intellij.dupLocator.iterators.FilteringNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
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
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.handlers.*;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class StructuralSearchProfileBase extends StructuralSearchProfile {
  private static final String DELIMITER_CHARS = ",;.[]{}():";

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
        final CompiledPattern pattern = globalVisitor.getContext().getPattern();
        final MatchingHandler handler = pattern.getHandler(element);

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
          final MatchingHandler delegate = ((LightTopLevelMatchingHandler)handler).getDelegate();
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

        final PsiFile file = start.getContainingFile();
        if (file != null) {
          final Language fileLanguage = file.getLanguage();
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
  public NodeFilter getLexicalNodesFilter() {
    return element -> DuplocatorUtil.isIgnoredNode(element);
  }

  private static boolean containsOnlyDelimiters(String s) {
    for (int i = 0, n = s.length(); i < n; i++) {
      if (DELIMITER_CHARS.indexOf(s.charAt(i)) < 0) {
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

  @Override
  public void checkReplacementPattern(Project project, ReplaceOptions options) {}

  @Override
  public StructuralReplaceHandler getReplaceHandler(@NotNull Project project, @NotNull ReplaceOptions replaceOptions) {
    return new DocumentBasedReplaceHandler(project);
  }

  static boolean canBePatternVariable(PsiElement element) {
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

  static boolean canBePatternVariableValue(PsiElement element) {
    // can be leaf element! (ex. var a = 1 <-> var $a$ = 1)
    return !containsOnlyDelimiters(element.getText());
  }

  @Override
  public boolean canBeVarDelimiter(@NotNull PsiElement element) {
    final ASTNode node = element.getNode();
    return node != null && getVariableDelimiters().contains(node.getElementType());
  }

  protected TokenSet getVariableDelimiters() {
    return TokenSet.EMPTY;
  }

  // todo: support expression patterns
  // todo: support {statement;} = statement; (node has only non-lexical child)

  private class MyCompilingVisitor extends PsiRecursiveElementVisitor {
    private final GlobalCompilingVisitor myGlobalVisitor;
    private final PsiElement myTopElement;

    private Pattern[] mySubstitutionPatterns;

    MyCompilingVisitor(GlobalCompilingVisitor globalVisitor, PsiElement topElement) {
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
      final CompiledPattern pattern = myGlobalVisitor.getContext().getPattern();

      if (myGlobalVisitor.getCodeBlockLevel() == 0) {
        initTopLevelElement(element);
        return;
      }

      if (canBePatternVariable(element) && pattern.isRealTypedVar(element)) {
        myGlobalVisitor.handle(element);
        final MatchingHandler handler = pattern.getHandler(element);
        handler.setFilter(new NodeFilter() {
          @Override
          public boolean accepts(PsiElement other) {
            return canBePatternVariableValue(other);
          }
        });

        super.visitElement(element);

        return;
      }

      super.visitElement(element);

      if (myGlobalVisitor.getContext().getSearchHelper().doOptimizing() && element instanceof LeafElement) {
        final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
        if (parserDefinition != null) {
          final String text = element.getText();

          // todo: support variables inside comments
          if (StringUtil.isJavaIdentifier(text)) {
            myGlobalVisitor.processTokenizedName(text, true, GlobalCompilingVisitor.OccurenceKind.CODE);
          }
        }
      }
    }

    private void visitLiteral(PsiElement literal) {
      final String value = literal.getText();

      if (StringUtil.isQuotedString(value)) {
        if (mySubstitutionPatterns == null) {
          final String[] prefixes = myGlobalVisitor.getContext().getPattern().getTypedVarPrefixes();
          mySubstitutionPatterns = StructuralSearchUtil.createPatterns(prefixes);
        }

        for (Pattern substitutionPattern : mySubstitutionPatterns) {
          @Nullable final MatchingHandler handler =
            myGlobalVisitor.processPatternStringWithFragments(value, GlobalCompilingVisitor.OccurenceKind.LITERAL, substitutionPattern);

          if (handler != null) {
            literal.putUserData(CompiledPattern.HANDLER_KEY, handler);
            break;
          }
        }
      }
    }

    private void initTopLevelElement(PsiElement element) {
      final CompiledPattern pattern = myGlobalVisitor.getContext().getPattern();

      final PsiElement newElement = SkippingHandler.skipNodeIfNeccessary(element);

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

            final MatchingHandler matchingHandler = pattern.getHandler(el);
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

    MyMatchingVisitor(GlobalMatchingVisitor globalVisitor) {
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
            .match(descriptor1, descriptor2, myGlobalVisitor, Collections.emptySet(), null);
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
        final PsiElement patternChild = element.getFirstChild();
        final PsiElement matchedChild = myGlobalVisitor.getElement().getFirstChild();

        final FilteringNodeIterator patternIterator = new SsrFilteringNodeIterator(patternChild);
        final FilteringNodeIterator matchedIterator = new SsrFilteringNodeIterator(matchedChild);

        final boolean matched = myGlobalVisitor.matchSequentially(patternIterator, matchedIterator);
        myGlobalVisitor.setResult(matched);
      }
    }

    private void visitLiteral(PsiElement literal) {
      final PsiElement l2 = myGlobalVisitor.getElement();
      final MatchingHandler handler = (MatchingHandler)literal.getUserData(CompiledPattern.HANDLER_KEY);

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

    MySubstitutionHandler(String name, boolean target, int minOccurs, int maxOccurs, boolean greedy) {
      super(name, target, minOccurs, maxOccurs, greedy);
      myExceptedNodes = new HashSet<>();
    }

    @Override
    public boolean matchSequentially(NodeIterator patternNodes, NodeIterator matchNodes, MatchContext context) {
      if (doMatchSequentially(patternNodes, matchNodes, context)) {
        return true;
      }
      final PsiElement current = patternNodes.current();
      if (current != null) {
        myExceptedNodes.add(current);
      }
      final boolean result = doMatchSequentiallyBySimpleHandler(patternNodes, matchNodes, context);
      myExceptedNodes.remove(current);
      return result;
    }
  }
}
