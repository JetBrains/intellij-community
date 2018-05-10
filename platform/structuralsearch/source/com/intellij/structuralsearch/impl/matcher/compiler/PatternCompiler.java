// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.DelegatingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.predicates.*;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles the handlers for usability
 */
public class PatternCompiler {
  private static final Object LOCK = new Object();
  private static SoftReference<CompiledPattern> ourLastCompiledPattern;
  private static MatchOptions ourLastMatchOptions;
  private static boolean ourLastCompileSuccessful = true;
  private static CompileContext lastTestingContext;

  public static CompiledPattern compilePattern(Project project,MatchOptions options, boolean checkForErrors)
    throws MalformedPatternException, NoMatchFoundException {
    if (!checkForErrors) {
      synchronized (LOCK) {
        if (options.equals(ourLastMatchOptions) &&
            (!(options.getScope() instanceof GlobalSearchScope) || options.getScope() == ourLastMatchOptions.getScope())) {
          if (!ourLastCompileSuccessful) return null;
          assert ourLastCompiledPattern != null;
          final CompiledPattern lastCompiledPattern = ourLastCompiledPattern.get();
          if (lastCompiledPattern != null) {
            return lastCompiledPattern;
          }
        }
      }
    }
    return !ApplicationManager.getApplication().isDispatchThread()
           ? ReadAction.compute(() -> doCompilePattern(project, options, checkForErrors))
           : doCompilePattern(project, options, checkForErrors);
  }

  @NotNull
  private static CompiledPattern doCompilePattern(Project project, MatchOptions options, boolean checkForErrors)
    throws MalformedPatternException, NoMatchFoundException {

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(options.getFileType());
    assert profile != null : "no profile found for " + options.getFileType().getDescription();
    final CompiledPattern result = profile.createCompiledPattern();

    final String[] prefixes = result.getTypedVarPrefixes();
    assert prefixes.length > 0;

    final CompileContext context = new CompileContext(result, options, project);
    if (ApplicationManager.getApplication().isUnitTestMode()) lastTestingContext = context;

    try {
      final List<PsiElement> elements = compileByAllPrefixes(project, options, result, context, prefixes);
      final CompiledPattern pattern = context.getPattern();
      try {
        checkForUnknownVariables(pattern, elements);
        pattern.setNodes(elements);
        synchronized (LOCK) {
          ourLastMatchOptions = options.copy();
          ourLastCompiledPattern = new SoftReference<>(result);
          ourLastCompileSuccessful = true;
        }
      } catch (MalformedPatternException e) {
        synchronized (LOCK) {
          ourLastMatchOptions = options.copy();
          ourLastCompiledPattern = null;
          ourLastCompileSuccessful = false;
        }
        throw e;
      }
      if (checkForErrors) {
        profile.checkSearchPattern(pattern);
        optimizeScope(options, result, context);
      }
      return result;
    } finally {
      context.clear();
    }
  }

  private static void optimizeScope(MatchOptions options, CompiledPattern result, CompileContext context)
    throws NoMatchFoundException {

    final OptimizingSearchHelper searchHelper = context.getSearchHelper();
    if (searchHelper.doOptimizing() && searchHelper.isScannedSomething()) {
      final List<PsiFile> filesToScan = new SmartList<>();
      final SearchScope scope = options.getScope();
      for (final PsiFile file : searchHelper.getFilesSetToScan()) {
        if (scope.contains(file.getVirtualFile())) filesToScan.add(file);
      }

      if (filesToScan.isEmpty()) {
        throw new NoMatchFoundException(SSRBundle.message("ssr.will.not.find.anything", scope.getDisplayName()));
      }
      result.setScope(new LocalSearchScope(PsiUtilCore.toPsiElementArray(filesToScan)));
    }
  }

  private static void checkForUnknownVariables(final CompiledPattern pattern, List<PsiElement> elements)
    throws MalformedPatternException {

    for (PsiElement element : elements) {
      pattern.putVariableNode(Configuration.CONTEXT_VAR_NAME, element);
      element.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (element.getUserData(CompiledPattern.HANDLER_KEY) != null) {
            return;
          }
          super.visitElement(element);

          if (!(element instanceof LeafElement)) {
            return;
          }
          final String text = element.getText();
          if (!pattern.isTypedVar(text)) {
            for (String prefix : pattern.getTypedVarPrefixes()) {
              if (text.contains(prefix)) {
                throw new MalformedPatternException();
              }
            }
            return;
          }
          final MatchingHandler handler = pattern.getHandler(pattern.getTypedVarString(element));
          if (handler == null) {
            throw new MalformedPatternException();
          }
        }
      });
      element.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          collectNode(element, element.getUserData(CompiledPattern.HANDLER_KEY));
          super.visitElement(element);

          if (element instanceof LeafElement) {
            collectNode(element, pattern.getHandler(pattern.getTypedVarString(element)));
          }
        }

        private void collectNode(PsiElement element, Object handler) {
          if (handler instanceof DelegatingHandler) {
            handler = ((DelegatingHandler)handler).getDelegate();
          }
          if (handler instanceof SubstitutionHandler){
            pattern.putVariableNode(((SubstitutionHandler)handler).getName(), element);
          }
        }
      });
    }
  }

  @TestOnly
  public static String getLastFindPlan() {
    return ((TestModeOptimizingSearchHelper)lastTestingContext.getSearchHelper()).getSearchPlan();
  }

  @NotNull
  private static List<PsiElement> compileByAllPrefixes(Project project,
                                                       MatchOptions options,
                                                       CompiledPattern pattern,
                                                       CompileContext context,
                                                       String[] applicablePrefixes) throws MalformedPatternException {
    if (applicablePrefixes.length == 0) {
      return Collections.emptyList();
    }

    List<PsiElement> elements = doCompile(project, options, pattern, new ConstantPrefixProvider(applicablePrefixes[0]), context);
    if (elements.isEmpty()) {
      return elements;
    }

    final PsiFile file = elements.get(0).getContainingFile();
    if (file == null) {
      return elements;
    }

    final PsiElement last = elements.get(elements.size() - 1);
    final Pattern[] patterns = new Pattern[applicablePrefixes.length];

    for (int i = 0; i < applicablePrefixes.length; i++) {
      patterns[i] = Pattern.compile(StructuralSearchUtil.shieldRegExpMetaChars(applicablePrefixes[i]) + "\\w+\\b");
    }

    final int[] varEndOffsets = findAllTypedVarOffsets(file, patterns);

    final int patternEndOffset = last.getTextRange().getEndOffset();
    if (elements.isEmpty() || checkErrorElements(file, patternEndOffset, patternEndOffset, varEndOffsets, true) != Boolean.TRUE) {
      return elements;
    }

    final int varCount = varEndOffsets.length;
    final String[] prefixSequence = new String[varCount];

    for (int i = 0; i < varCount; i++) {
      prefixSequence[i] = applicablePrefixes[0];
    }

    final List<PsiElement> finalElements =
      compileByPrefixes(project, options, pattern, context, applicablePrefixes, patterns, prefixSequence, 0);
    return finalElements != null
           ? finalElements
           : doCompile(project, options, pattern, new ConstantPrefixProvider(applicablePrefixes[0]), context);
  }

  @Nullable
  private static List<PsiElement> compileByPrefixes(Project project,
                                                    MatchOptions options,
                                                    CompiledPattern pattern,
                                                    CompileContext context,
                                                    String[] applicablePrefixes,
                                                    Pattern[] substitutionPatterns,
                                                    String[] prefixSequence,
                                                    int index) throws MalformedPatternException {
    if (index >= prefixSequence.length) {
      final List<PsiElement> elements = doCompile(project, options, pattern, new ArrayPrefixProvider(prefixSequence), context);
      if (elements.isEmpty()) {
        return elements;
      }

      final PsiElement parent = elements.get(0).getParent();
      final PsiElement last = elements.get(elements.size() - 1);
      final int[] varEndOffsets = findAllTypedVarOffsets(parent.getContainingFile(), substitutionPatterns);
      final int patternEndOffset = last.getTextRange().getEndOffset();
      return checkErrorElements(parent, patternEndOffset, patternEndOffset, varEndOffsets, false) != Boolean.TRUE
             ? elements
             : null;
    }

    String[] alternativeVariant = null;

    for (String applicablePrefix : applicablePrefixes) {
      prefixSequence[index] = applicablePrefix;

      List<PsiElement> elements = doCompile(project, options, pattern, new ArrayPrefixProvider(prefixSequence), context);
      if (elements.isEmpty()) {
        return elements;
      }

      final PsiFile file = elements.get(0).getContainingFile();
      if (file == null) {
        return elements;
      }

      final int[] varEndOffsets = findAllTypedVarOffsets(file, substitutionPatterns);
      final int offset = varEndOffsets[index];

      final int patternEndOffset = elements.get(elements.size() - 1).getTextRange().getEndOffset();
      final Boolean result = checkErrorElements(file, offset, patternEndOffset, varEndOffsets, false);

      if (result == Boolean.TRUE) {
        continue;
      }

      if (result == Boolean.FALSE || (result == null && alternativeVariant == null)) {
        final List<PsiElement> finalElements =
          compileByPrefixes(project, options, pattern, context, applicablePrefixes, substitutionPatterns, prefixSequence, index + 1);
        if (finalElements != null) {
          if (result == Boolean.FALSE) {
            return finalElements;
          }
          alternativeVariant = new String[prefixSequence.length];
          System.arraycopy(prefixSequence, 0, alternativeVariant, 0, prefixSequence.length);
        }
      }
    }

    return alternativeVariant != null ?
           compileByPrefixes(project, options, pattern, context, applicablePrefixes, substitutionPatterns, alternativeVariant, index + 1) :
           null;
  }

  @NotNull
  private static int[] findAllTypedVarOffsets(final PsiFile file, final Pattern[] substitutionPatterns) {
    final TIntHashSet result = new TIntHashSet();

    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);

        if (element instanceof LeafElement) {
          final String text = element.getText();

          for (Pattern pattern : substitutionPatterns) {
            final Matcher matcher = pattern.matcher(text);

            while (matcher.find()) {
              result.add(element.getTextRange().getStartOffset() + matcher.end());
            }
          }
        }
      }
    });

    final int[] resultArray = result.toArray();
    Arrays.sort(resultArray);
    return resultArray;
  }


  /**
   * False: there are no error elements before offset, except patternEndOffset
   * Null: there are only error elements located exactly after template variables or at the end of the pattern
   * True: otherwise
   */
  @Nullable
  private static Boolean checkErrorElements(PsiElement element,
                                            final int offset,
                                            final int patternEndOffset,
                                            final int[] varEndOffsets,
                                            final boolean strict) {
    final TIntArrayList errorOffsets = new TIntArrayList();
    final boolean[] containsErrorTail = {false};
    final TIntHashSet varEndOffsetsSet = new TIntHashSet(varEndOffsets);

    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitErrorElement(PsiErrorElement element) {
        super.visitErrorElement(element);

        final int startOffset = element.getTextRange().getStartOffset();

        if ((strict || !varEndOffsetsSet.contains(startOffset)) && startOffset != patternEndOffset) {
          errorOffsets.add(startOffset);
        }

        if (startOffset == offset) {
          containsErrorTail[0] = true;
        }
      }
    });

    for (int i = 0; i < errorOffsets.size(); i++) {
      final int errorOffset = errorOffsets.get(i);
      if (errorOffset <= offset) {
        return true;
      }
    }
    return containsErrorTail[0] ? null : false;
  }

  private interface PrefixProvider {
    String getPrefix(int varIndex);
  }

  private static class ConstantPrefixProvider implements PrefixProvider {
    private final String myPrefix;

    ConstantPrefixProvider(String prefix) {
      myPrefix = prefix;
    }

    @Override
    public String getPrefix(int varIndex) {
      return myPrefix;
    }
  }

  private static class ArrayPrefixProvider implements PrefixProvider {
    private final String[] myPrefixes;

    ArrayPrefixProvider(String[] prefixes) {
      myPrefixes = prefixes;
    }

    @Override
    public String getPrefix(int varIndex) {
      if (varIndex >= myPrefixes.length) return null;
      return myPrefixes[varIndex];
    }
  }

  private static List<PsiElement> doCompile(Project project,
                                            MatchOptions options,
                                            CompiledPattern result,
                                            PrefixProvider prefixProvider,
                                            CompileContext context) throws MalformedPatternException {
    result.clearHandlers();

    final StringBuilder buf = new StringBuilder();

    final Template template = TemplateManager.getInstance(project).createTemplate("", "", options.getSearchPattern());

    final int segmentsCount = template.getSegmentsCount();
    final String text = template.getTemplateText();
    int prevOffset = 0;
    final Set<String> seen = ContainerUtil.newTroveSet();
    final Set<String> variableNames = ContainerUtil.newTroveSet();

    for(int i = 0; i < segmentsCount; i++) {
      final int offset = template.getSegmentOffset(i);
      final String name = template.getSegmentName(i);

      final String prefix = prefixProvider.getPrefix(i);
      if (prefix == null) {
        throw new MalformedPatternException();
      }

      final String compiledName = prefix + name;
      buf.append(text, prevOffset, offset).append(compiledName);

      variableNames.add(name);
      if (seen.add(compiledName)) {
        // the same variable can occur multiple times in a single template
        // no need to process it more than once

        MatchVariableConstraint constraint = options.getVariableConstraint(name);
        if (constraint == null) {
          // we do not edited the constraints
          constraint = new MatchVariableConstraint();
          constraint.setName(name);
          options.addVariableConstraint(constraint);
        }

        SubstitutionHandler handler = result.createSubstitutionHandler(
          name,
          compiledName,
          constraint.isPartOfSearchResults(),
          constraint.getMinCount(),
          constraint.getMaxCount(),
          constraint.isGreedy()
        );

        if (constraint.isWithinHierarchy()) {
          handler.setSubtype(true);
        }

        if (constraint.isStrictlyWithinHierarchy()) {
          handler.setStrictSubtype(true);
        }

        if (!StringUtil.isEmptyOrSpaces(constraint.getRegExp())) {
          MatchPredicate predicate = new RegExpPredicate(
            constraint.getRegExp(),
            options.isCaseSensitiveMatch(),
            name,
            constraint.isWholeWordsOnly(),
            constraint.isPartOfSearchResults()
          );
          if (constraint.isInvertRegExp()) {
            predicate = new NotPredicate(predicate);
          }
          addPredicate(handler, predicate);
        }

        if (!StringUtil.isEmptyOrSpaces(constraint.getReferenceConstraint())) {
          MatchPredicate predicate = new ReferencePredicate(constraint.getReferenceConstraint(), options.getFileType(), project);

          if (constraint.isInvertReference()) {
            predicate = new NotPredicate(predicate);
          }
          addPredicate(handler, predicate);
        }

        addExtensionPredicates(options, constraint, handler);
        addScriptConstraint(project, name, constraint, handler, variableNames);

        if (!StringUtil.isEmptyOrSpaces(constraint.getContainsConstraint())) {
          MatchPredicate predicate = new ContainsPredicate(name, constraint.getContainsConstraint());
          if (constraint.isInvertContainsConstraint()) {
            predicate = new NotPredicate(predicate);
          }
          addPredicate(handler, predicate);
        }

        if (!StringUtil.isEmptyOrSpaces(constraint.getWithinConstraint())) {
          assert false;
        }
      }
      prevOffset = offset;
    }

    MatchVariableConstraint constraint = options.getVariableConstraint(Configuration.CONTEXT_VAR_NAME);
    if (constraint != null) {
      SubstitutionHandler handler = result.createSubstitutionHandler(
        Configuration.CONTEXT_VAR_NAME,
        Configuration.CONTEXT_VAR_NAME,
        constraint.isPartOfSearchResults(),
        constraint.getMinCount(),
        constraint.getMaxCount(),
        constraint.isGreedy()
      );

      if (!StringUtil.isEmptyOrSpaces(constraint.getWithinConstraint())) {
        MatchPredicate predicate = new WithinPredicate(constraint.getWithinConstraint(), options.getFileType(), project);
        if (constraint.isInvertWithinConstraint()) {
          predicate = new NotPredicate(predicate);
        }
        addPredicate(handler, predicate);
      }

      addExtensionPredicates(options, constraint, handler);
      addScriptConstraint(project, Configuration.CONTEXT_VAR_NAME, constraint, handler, variableNames);
    }

    buf.append(text.substring(prevOffset));

    PsiElement[] patternElements;
    try {
      patternElements = MatcherImplUtil.createTreeFromText(buf.toString(), PatternTreeContext.Block, options.getFileType(),
                                                           options.getDialect(), options.getPatternContext(), project, false);
      if (patternElements.length == 0) throw new MalformedPatternException();
    } catch (IncorrectOperationException e) {
      throw new MalformedPatternException(e.getMessage());
    }

    NodeFilter filter = LexicalNodesFilter.getInstance();
    List<PsiElement> elements = new SmartList<>();
    for (PsiElement element : patternElements) {
      if (!filter.accepts(element)) {
        elements.add(element);
      }
    }

    final GlobalCompilingVisitor compilingVisitor = new GlobalCompilingVisitor();
    compilingVisitor.compile(elements.toArray(PsiElement.EMPTY_ARRAY), context);
    new DeleteNodesAction(compilingVisitor.getLexicalNodes()).run();
    return elements;
  }

  private static void addExtensionPredicates(MatchOptions options, MatchVariableConstraint constraint, SubstitutionHandler handler) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(options.getFileType());
    assert profile != null;
    for (MatchPredicate matchPredicate : profile.getCustomPredicates(constraint, handler.getName(), options)) {
      addPredicate(handler, matchPredicate);
    }
  }

  private static void addScriptConstraint(Project project, String name, MatchVariableConstraint constraint,
                                          SubstitutionHandler handler, Set<String> variableNames)
    throws MalformedPatternException {
    if (constraint.getScriptCodeConstraint()!= null && constraint.getScriptCodeConstraint().length() > 2) {
      final String script = StringUtil.unquoteString(constraint.getScriptCodeConstraint());
      final String problem = ScriptSupport.checkValidScript(script);
      if (problem != null) {
        throw new MalformedPatternException("Script constraint for " + constraint.getName() + " has problem " + problem);
      }
      addPredicate(handler, new ScriptPredicate(project, name, script, variableNames));
    }
  }

  private static void addPredicate(SubstitutionHandler handler, MatchPredicate predicate) {
    handler.setPredicate((handler.getPredicate() == null) ? predicate : new AndPredicate(handler.getPredicate(), predicate));
  }
}