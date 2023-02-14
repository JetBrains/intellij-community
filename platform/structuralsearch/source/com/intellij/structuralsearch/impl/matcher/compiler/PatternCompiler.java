// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.search.GlobalSearchScope;
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
import groovy.lang.Script;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles the handlers for usability
 */
public final class PatternCompiler {
  private static String ourLastSearchPlan;

  /**
   * @return the compiled pattern, or null when there is no structural search profile found for the file type in the match options.
   */
  public static CompiledPattern compilePattern(Project project, MatchOptions options, boolean checkForErrors, boolean optimizeScope)
    throws MalformedPatternException, NoMatchFoundException {
    return ReadAction.nonBlocking(() -> doCompilePattern(project, options, checkForErrors, optimizeScope)).executeSynchronously();
  }

  @Nullable
  private static CompiledPattern doCompilePattern(@NotNull Project project, @NotNull MatchOptions options,
                                                  boolean checkForErrors, boolean optimizeScope)
    throws MalformedPatternException, NoMatchFoundException {

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(options.getFileType());
    if (profile == null) {
      return null;
    }
    final CompiledPattern result = profile.createCompiledPattern();

    final String[] prefixes = result.getTypedVarPrefixes();
    assert prefixes.length > 0;

    final CompileContext context = new CompileContext(result, options, project);

    try {
      final List<PsiElement> elements = compileByAllPrefixes(project, options, result, context, prefixes, checkForErrors);
      if (elements.isEmpty()) {
        return null;
      }
      final CompiledPattern pattern = context.getPattern();
      collectVariableNodes(pattern, elements, checkForErrors);
      pattern.setNodes(elements);
      if (checkForErrors) {
        profile.checkSearchPattern(pattern);
      }
      if (optimizeScope) {
        optimizeScope(options, checkForErrors, context, result);
      }
      return result;
    }
    finally {
      context.clear();
    }
  }

  private static void optimizeScope(MatchOptions options, boolean checkForErrors, CompileContext context, CompiledPattern result)
    throws NoMatchFoundException {

    final OptimizingSearchHelper searchHelper = context.getSearchHelper();
    if (searchHelper.doOptimizing() && searchHelper.isScannedSomething()) {
      final Set<VirtualFile> filesToScan = searchHelper.getFilesSetToScan();

      final GlobalSearchScope scope = (GlobalSearchScope)options.getScope();
      assert scope != null;
      if (checkForErrors && filesToScan.isEmpty()) {
        throw new NoMatchFoundException(SSRBundle.message("ssr.will.not.find.anything", scope.getDisplayName()));
      }
      result.setScope(scope.isSearchInLibraries()
                      ? GlobalSearchScope.filesWithLibrariesScope(context.getProject(), filesToScan)
                      : GlobalSearchScope.filesWithoutLibrariesScope(context.getProject(), filesToScan));
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ourLastSearchPlan = ((TestModeOptimizingSearchHelper)searchHelper).getSearchPlan();
    }
  }

  private static void collectVariableNodes(final CompiledPattern pattern, List<? extends PsiElement> elements, boolean checkForErrors)
    throws MalformedPatternException {

    for (PsiElement element : elements) {
      pattern.putVariableNode(Configuration.CONTEXT_VAR_NAME, element);
      if (checkForErrors) {
        checkForUnknownVariables(pattern, element);
      }
      element.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          collectNode(element, element.getUserData(CompiledPattern.HANDLER_KEY));
          super.visitElement(element);

          if (element instanceof LeafElement) {
            collectNode(element, pattern.getHandler(pattern.getTypedVarString(element)));
          }
        }

        private void collectNode(PsiElement element, MatchingHandler handler) {
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

  private static void checkForUnknownVariables(CompiledPattern pattern, PsiElement element) {
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
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
  }

  @TestOnly
  public static String getLastSearchPlan() {
    return ourLastSearchPlan;
  }

  @NotNull
  private static List<PsiElement> compileByAllPrefixes(@NotNull Project project,
                                                       @NotNull MatchOptions options,
                                                       @NotNull CompiledPattern pattern,
                                                       @NotNull CompileContext context,
                                                       String @NotNull [] applicablePrefixes,
                                                       boolean checkForErrors) throws MalformedPatternException {
    if (applicablePrefixes.length == 0) {
      return Collections.emptyList();
    }

    final List<PsiElement> elements =
      doCompile(project, options, pattern, new ConstantPrefixProvider(applicablePrefixes[0]), context, checkForErrors);
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
      patterns[i] = Pattern.compile(MatchUtil.shieldRegExpMetaChars(applicablePrefixes[i]) + "\\w+\\b");
    }

    final int[] varEndOffsets = findAllTypedVarOffsets(file, patterns);

    final int patternEndOffset = last.getTextRange().getEndOffset();
    if (elements.isEmpty() || checkErrorElements(file, patternEndOffset, patternEndOffset, varEndOffsets, true) != Boolean.TRUE) {
      return elements;
    }

    final int varCount = varEndOffsets.length;
    final String[] prefixSequence = new String[varCount];

    Arrays.fill(prefixSequence, applicablePrefixes[0]);

    final List<PsiElement> finalElements =
      compileByPrefixes(project, options, pattern, context, applicablePrefixes, patterns, prefixSequence, 0, checkForErrors);
    return finalElements != null
           ? finalElements
           : doCompile(project, options, pattern, new ConstantPrefixProvider(applicablePrefixes[0]), context, checkForErrors);
  }

  @Nullable
  private static List<PsiElement> compileByPrefixes(Project project,
                                                    MatchOptions options,
                                                    CompiledPattern pattern,
                                                    CompileContext context,
                                                    String[] applicablePrefixes,
                                                    Pattern[] substitutionPatterns,
                                                    String[] prefixSequence,
                                                    int index,
                                                    boolean checkForErrors) throws MalformedPatternException {
    if (index >= prefixSequence.length) {
      final List<PsiElement> elements =
        doCompile(project, options, pattern, new ArrayPrefixProvider(prefixSequence), context, checkForErrors);
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

      final List<PsiElement> elements =
        doCompile(project, options, pattern, new ArrayPrefixProvider(prefixSequence), context, checkForErrors);
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

      if (result == Boolean.FALSE || result == null && alternativeVariant == null) {
        final List<PsiElement> finalElements =
          compileByPrefixes(project, options, pattern, context, applicablePrefixes, substitutionPatterns, prefixSequence, index + 1, checkForErrors);
        if (finalElements != null) {
          if (result == Boolean.FALSE) {
            return finalElements;
          }
          alternativeVariant = prefixSequence.clone();
        }
      }
    }

    return alternativeVariant != null ?
           compileByPrefixes(project, options, pattern, context, applicablePrefixes, substitutionPatterns, alternativeVariant, index + 1, checkForErrors) :
           null;
  }

  private static int @NotNull [] findAllTypedVarOffsets(final PsiFile file, final Pattern[] substitutionPatterns) {
    final IntSet result = new IntOpenHashSet();

    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
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

    final int[] resultArray = result.toIntArray();
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
    final IntList errorOffsets = new IntArrayList();
    final boolean[] containsErrorTail = {false};
    final IntSet varEndOffsetsSet = new IntOpenHashSet(varEndOffsets);

    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitErrorElement(@NotNull PsiErrorElement element) {
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
      final int errorOffset = errorOffsets.getInt(i);
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

    ConstantPrefixProvider(@NotNull String prefix) {
      myPrefix = prefix;
    }

    @NotNull
    @Override
    public String getPrefix(int varIndex) {
      return myPrefix;
    }
  }

  private static class ArrayPrefixProvider implements PrefixProvider {
    private final String[] myPrefixes;

    ArrayPrefixProvider(String @NotNull [] prefixes) {
      myPrefixes = prefixes;
    }

    @Override
    public String getPrefix(int varIndex) {
      if (varIndex >= myPrefixes.length) return null;
      return myPrefixes[varIndex];
    }
  }

  @NotNull
  private static List<PsiElement> doCompile(@NotNull Project project,
                                            @NotNull MatchOptions options,
                                            @NotNull CompiledPattern result,
                                            @NotNull PrefixProvider prefixProvider,
                                            @NotNull CompileContext context,
                                            boolean checkForErrors) throws MalformedPatternException {
    result.clearHandlers();

    final StringBuilder buf = new StringBuilder();

    final Template template = TemplateManager.getInstance(project).createTemplate("", "", options.getSearchPattern());

    final int segmentsCount = template.getSegmentsCount();
    final String text = template.getTemplateText();
    int prevOffset = 0;
    final Set<String> variableNames = new HashSet<>();

    final LanguageFileType fileType = options.getFileType();
    assert fileType != null;
    for(int i = 0; i < segmentsCount; i++) {
      final int offset = template.getSegmentOffset(i);
      final String name = template.getSegmentName(i);

      final String prefix = prefixProvider.getPrefix(i);
      if (prefix == null) {
        if (checkForErrors) throw new MalformedPatternException();
        return Collections.emptyList();
      }

      final String compiledName = prefix + name;
      buf.append(text, prevOffset, offset).append(compiledName);

      final boolean repeated = !variableNames.add(name);
      final SubstitutionHandler existing = (SubstitutionHandler)result.getHandler(compiledName);
      if (existing != null) {
        existing.setRepeatedVar(repeated);
      }
      else {
        // the same variable can occur multiple times in a single template
        // no need to process it more than once
        try {
          MatchVariableConstraint constraint = options.getVariableConstraint(name);
          if (constraint == null) {
            // we do not edit the constraints
            constraint = options.addNewVariableConstraint(name);
          }

          final SubstitutionHandler handler = result.createSubstitutionHandler(
            name,
            compiledName,
            constraint.isPartOfSearchResults(),
            constraint.getMinCount(),
            constraint.getMaxCount(),
            constraint.isGreedy()
          );
          handler.setRepeatedVar(repeated);

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
            MatchPredicate predicate = new ReferencePredicate(constraint.getReferenceConstraint(), fileType, project);
            if (constraint.isInvertReference()) {
              predicate = new NotPredicate(predicate);
            }
            addPredicate(handler, predicate);
          }

          addExtensionPredicates(options, constraint, handler);
          addScriptConstraint(project, name, constraint, handler, variableNames, options, checkForErrors);

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
        } catch (MalformedPatternException e) {
          if (checkForErrors) throw e;
        }
      }
      prevOffset = offset;
    }

    final MatchVariableConstraint constraint = options.getVariableConstraint(Configuration.CONTEXT_VAR_NAME);
    if (constraint != null) {
      final SubstitutionHandler handler = result.createSubstitutionHandler(
        Configuration.CONTEXT_VAR_NAME,
        Configuration.CONTEXT_VAR_NAME,
        constraint.isPartOfSearchResults(),
        constraint.getMinCount(),
        constraint.getMaxCount(),
        constraint.isGreedy()
      );

      try {
        if (!StringUtil.isEmptyOrSpaces(constraint.getWithinConstraint())) {
          MatchPredicate predicate = new WithinPredicate(constraint.getWithinConstraint(), fileType, project);
          if (constraint.isInvertWithinConstraint()) {
            predicate = new NotPredicate(predicate);
          }
          addPredicate(handler, predicate);
        }

        addExtensionPredicates(options, constraint, handler);
        addScriptConstraint(project, Configuration.CONTEXT_VAR_NAME, constraint, handler, variableNames, options, checkForErrors);
      }
      catch (MalformedPatternException e) {
        if (checkForErrors) throw e;
      }
    }

    buf.append(text.substring(prevOffset));

    final PsiElement[] patternElements;
    try {
      final PatternContextInfo contextInfo = new PatternContextInfo(PatternTreeContext.Block,
                                                                    options.getPatternContext(),
                                                                    constraint != null ? constraint.getContextConstraint() : null);
      final Language dialect = options.getDialect();
      assert dialect != null;
      patternElements = MatcherImplUtil.createTreeFromText(buf.toString(), contextInfo, fileType, dialect, project, false);
      if (patternElements.length == 0 && checkForErrors) throw new MalformedPatternException();
    }
    catch (IncorrectOperationException e) {
      if (checkForErrors) throw new MalformedPatternException(e.getMessage());
      return Collections.emptyList();
    }

    final NodeFilter filter = LexicalNodesFilter.getInstance();
    final List<PsiElement> elements = new SmartList<>();
    for (PsiElement element : patternElements) {
      if (!filter.accepts(element)) {
        elements.add(element);
      }
    }

    final GlobalCompilingVisitor compilingVisitor = new GlobalCompilingVisitor();
    try {
      compilingVisitor.compile(elements.toArray(PsiElement.EMPTY_ARRAY), context);
    }
    catch (MalformedPatternException e) {
      if (checkForErrors) throw e;
    }
    new DeleteNodesAction(compilingVisitor.getLexicalNodes()).run();
    return elements;
  }

  private static void addExtensionPredicates(@NotNull MatchOptions options, @NotNull MatchVariableConstraint constraint, @NotNull SubstitutionHandler handler) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(options.getFileType());
    assert profile != null;
    for (MatchPredicate matchPredicate : profile.getCustomPredicates(constraint, handler.getName(), options)) {
      addPredicate(handler, matchPredicate);
    }
  }

  private static void addScriptConstraint(Project project, String name, MatchVariableConstraint constraint,
                                          SubstitutionHandler handler, Set<String> variableNames, MatchOptions matchOptions,
                                          boolean checkForErrors)
    throws MalformedPatternException {
    final String scriptCodeConstraint = constraint.getScriptCodeConstraint();
    if (scriptCodeConstraint.length() > 2) {
      final String scriptText = StringUtil.unquoteString(scriptCodeConstraint);
      try {
        final Script script = ScriptSupport.buildScript(name, scriptText, matchOptions);
        addPredicate(handler, new ScriptPredicate(project, name, script, variableNames));
      } catch (MalformedPatternException e) {
        if (checkForErrors) {
          throw new MalformedPatternException(
            SSRBundle.message("error.script.constraint.for.0.has.problem.1", constraint.getName(), e.getLocalizedMessage())
          );
        }
      }
    }
  }

  private static void addPredicate(SubstitutionHandler handler, @NotNull MatchPredicate predicate) {
    handler.setPredicate(handler.getPredicate() == null ? predicate : new AndPredicate(handler.getPredicate(), predicate));
  }
}