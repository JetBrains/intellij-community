// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.impl.matcher.*;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink;
import com.intellij.structuralsearch.plugin.util.DuplicateFilteringResultSink;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.structuralsearch.impl.matcher.iterators.SingleNodeIterator.newSingleNodeIterator;

/**
 * This class makes program structure tree matching:
 */
public class Matcher {
  static final Logger LOG = Logger.getInstance(Matcher.class);

  @SuppressWarnings("SSBasedInspection")
  private static final ThreadLocal<Set<String>> ourRecursionGuard = ThreadLocal.withInitial(() -> new HashSet<>());

  // project being worked on
  final Project project;

  // context of matching
  final MatchContext matchContext;
  private boolean isTesting;

  // visitor to delegate the real work
  private final GlobalMatchingVisitor visitor = new GlobalMatchingVisitor();
  private final TaskScheduler scheduler = new TaskScheduler();

  int totalFilesToScan;
  int scannedFilesCount;

  public Matcher(@NotNull Project project, @NotNull MatchOptions matchOptions) {
    this(project, matchOptions, PatternCompiler.compilePattern(project, matchOptions, false, true));
  }

  public Matcher(@NotNull Project project, @NotNull MatchOptions matchOptions, @NotNull CompiledPattern compiledPattern) {
    this.project = project;
    matchContext = new MatchContext();
    matchContext.setMatcher(visitor);
    visitor.setMatchContext(matchContext);

    matchContext.setOptions(matchOptions);
    matchContext.setPattern(compiledPattern);
  }

  public static Matcher buildMatcher(Project project, LanguageFileType fileType, String constraint) {
    if (StringUtil.isQuotedString(constraint)) {
      // keep old configurations working, also useful for testing
      final MatchOptions myMatchOptions = new MatchOptions();
      myMatchOptions.setFileType(fileType);
      myMatchOptions.fillSearchCriteria(StringUtil.unquoteString(constraint));
      return new Matcher(project, myMatchOptions);
    }
    else {
      final Set<String> set = ourRecursionGuard.get();
      if (!set.add(constraint)) {
        throw new MalformedPatternException("Pattern recursively references itself");
      }
      try {
        final Configuration configuration = ConfigurationManager.getInstance(project).findConfigurationByName(constraint);
        if (configuration == null) {
          throw new MalformedPatternException("Configuration '" + constraint + "' not found");
        }
        return new Matcher(project, configuration.getMatchOptions());
      } finally {
        set.remove(constraint);
        if (set.isEmpty()) {
          // we're finished with this thread local
          ourRecursionGuard.remove();
        }
      }
    }
  }

  public static void validate(Project project, MatchOptions options) {
    PatternCompiler.compilePattern(project, options, true, true);
  }

  public boolean checkIfShouldAttemptToMatch(NodeIterator matchedNodes) {
    final CompiledPattern pattern = matchContext.getPattern();
    final NodeIterator patternNodes = pattern.getNodes();
    try {
      while (true) {
        final PsiElement patternNode = patternNodes.current();
        if (patternNode == null) {
          return true;
        }
        final PsiElement matchedNode = matchedNodes.current();
        if (matchedNode == null) {
          return false;
        }
        final MatchingHandler matchingHandler = pattern.getHandler(patternNode);
        if (!matchingHandler.canMatch(patternNode, matchedNode, matchContext)) {
          return false;
        }
        matchedNodes.advance();
        patternNodes.advance();
      }
    } finally {
      patternNodes.reset();
      matchedNodes.reset();
    }
  }

  public void processMatchesInElement(NodeIterator matchedNodes) {
    try {
      visitor.matchContext(matchedNodes);
    } finally {
      matchedNodes.reset();
    }
  }

  public boolean matchNode(@NotNull PsiElement element) {
    matchContext.clear();
    final CollectingMatchResultSink sink = new CollectingMatchResultSink();
    matchContext.setSink(new DuplicateFilteringResultSink(sink));
    final CompiledPattern compiledPattern = matchContext.getPattern();
    if (compiledPattern == null) {
      return false;
    }
    matchContext.setShouldRecursivelyMatch(false);
    visitor.matchContext(newSingleNodeIterator(element));
    return !sink.getMatches().isEmpty();
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   */
  public void findMatches(MatchResultSink sink) throws MalformedPatternException, UnsupportedPatternException {
    matchContext.clear();
    matchContext.setSink(new DuplicateFilteringResultSink(sink));
    final CompiledPattern compiledPattern = matchContext.getPattern();
    if (compiledPattern == null) {
      return;
    }

    matchContext.getSink().setMatchingProcess( scheduler );
    scheduler.init();

    if (isTesting) {
      // testing mode;
      final LocalSearchScope scope = (LocalSearchScope)matchContext.getOptions().getScope();
      assert scope != null;
      final PsiElement[] elements = scope.getScope();

      final PsiElement parent = elements[0].getParent();
      if (matchContext.getPattern().getStrategy().continueMatching(parent != null ? parent : elements[0])) {
        visitor.matchContext(new SsrFilteringNodeIterator(new ArrayBackedNodeIterator(elements)));
      }
      else {
        final LanguageFileType fileType = matchContext.getOptions().getFileType();
        final Language language = fileType.getLanguage();
        for (PsiElement element : elements) {
          match(element, language);
        }
      }

      matchContext.getSink().matchingFinished();
      return;
    }
    findMatches();

    if (scheduler.getTaskQueueEndAction()==null) {
      scheduler.setTaskQueueEndAction(
        () -> matchContext.getSink().matchingFinished()
      );
    }

    scheduler.executeNext();
  }

  private void findMatches() {
    final MatchOptions options = matchContext.getOptions();
    final CompiledPattern compiledPattern = matchContext.getPattern();
    SearchScope searchScope = compiledPattern.getScope();
    final boolean ourOptimizedScope = searchScope != null;
    if (!ourOptimizedScope) searchScope = options.getScope();

    if (searchScope instanceof GlobalSearchScope) {
      final GlobalSearchScope scope = (GlobalSearchScope)searchScope;

      final ContentIterator ci = fileOrDir -> {
        if (!fileOrDir.isDirectory() && scope.contains(fileOrDir) && fileOrDir.getFileType() != FileTypes.UNKNOWN) {
          ++totalFilesToScan;
          scheduler.addOneTask(new MatchOneVirtualFile(fileOrDir));
        }
        return true;
      };

      final ProgressIndicator progress = matchContext.getSink().getProgressIndicator();
      ReadAction.run(() -> FileBasedIndex.getInstance().iterateIndexableFiles(ci, project, progress));
      if (progress != null) progress.setText2("");
    }
    else {
      final LocalSearchScope scope = (LocalSearchScope)searchScope;
      assert scope != null;
      final PsiElement[] elementsToScan = scope.getScope();
      totalFilesToScan = elementsToScan.length;

      for (int i = 0; i < elementsToScan.length; ++i) {
        final PsiElement psiElement = elementsToScan[i];

        if (psiElement == null) continue;
        scheduler.addOneTask(new MatchOnePsiFile(psiElement));
        if (ourOptimizedScope) elementsToScan[i] = null; // to prevent long PsiElement reference
      }
    }
  }

  public MatchContext getMatchContext() {
    return matchContext;
  }

  public Project getProject() {
    return project;
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @param source string for search
   * @return list of matches found
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  public List<MatchResult> testFindMatches(String source,
                                           boolean fileContext,
                                           LanguageFileType sourceFileType,
                                           boolean physicalSourceFile)
    throws MalformedPatternException, UnsupportedPatternException {

    final CollectingMatchResultSink sink = new CollectingMatchResultSink();
    final MatchOptions options = matchContext.getOptions();

    try {
      if (options.getScope() == null) {
        final PsiElement[] elements =
          MatcherImplUtil.createSourceTreeFromText(source, fileContext ? PatternTreeContext.File : PatternTreeContext.Block,
                                                   sourceFileType, project, physicalSourceFile);

        options.setScope(new LocalSearchScope(elements));
      }
      testFindMatches(sink);
    } finally {
      options.setScope(null);
    }

    return sink.getMatches();
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @param sink match result destination
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  public void testFindMatches(MatchResultSink sink) throws MalformedPatternException, UnsupportedPatternException {
    isTesting = true;
    try {
      findMatches(sink);
    } finally {
      isTesting = false;
    }
  }

  class TaskScheduler implements MatchingProcess {
    private List<Runnable> tasks = new SmartList<>();
    private boolean ended;
    private Runnable taskQueueEndAction;

    private boolean suspended;

    @Override
    public void stop() {
      ended = true;
    }

    @Override
    public void pause() {
      suspended = true;
    }

    @Override
    public void resume() {
      if (!suspended) return;
      suspended = false;
      executeNext();
    }

    @Override
    public boolean isSuspended() {
      return suspended;
    }

    @Override
    public boolean isEnded() {
      return ended;
    }

    void setTaskQueueEndAction(Runnable taskQueueEndAction) {
      this.taskQueueEndAction = taskQueueEndAction;
    }
    Runnable getTaskQueueEndAction () {
      return taskQueueEndAction;
    }

    void addOneTask(Runnable runnable) {
      tasks.add(runnable);
    }

    void executeNext() {
      while(!suspended && !ended) {
        if (tasks.isEmpty()) {
          ended = true;
          break;
        }

        final Runnable task = tasks.remove(tasks.size() - 1);
        try {
          task.run();
        }
        catch (ProcessCanceledException | StructuralSearchException e) {
          ended = true;
          clearSchedule();
          throw e;
        }
        catch (Throwable th) {
          LOG.error(th);
        }
      }

      if (ended) clearSchedule();
    }

    void init() {
      ended = false;
      suspended = false;
      PsiManager.getInstance(project).startBatchFilesProcessingMode();
    }

    private void clearSchedule() {
      if (tasks != null) {
        taskQueueEndAction.run();
        if (!project.isDisposed()) {
          PsiManager.getInstance(project).finishBatchFilesProcessingMode();
        }
        tasks = null;
      }
    }

  }

  /**
   * Initiates the matching process for given element
   * @param element the current search tree element
   */
  void match(@NotNull PsiElement element, Language language) {
    final MatchingStrategy strategy = matchContext.getPattern().getStrategy();

    if (strategy.continueMatching(element) && element.getLanguage().isKindOf(language)) {
      visitor.matchContext(newSingleNodeIterator(element));
      return;
    }
    for(PsiElement el = element.getFirstChild(); el != null; el = el.getNextSibling()) {
      match(el, language);
    }
    if (element instanceof PsiLanguageInjectionHost) {
      InjectedLanguageManager.getInstance(project).enumerateEx(element, element.getContainingFile(), false,
                                                               (injectedPsi, places) -> match(injectedPsi, language));
    }
  }

  /**
   * Tests if given element is matched by given pattern starting from target variable.
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  @NotNull
  public List<MatchResult> matchByDownUp(PsiElement element) throws MalformedPatternException, UnsupportedPatternException {
    matchContext.clear();
    final CollectingMatchResultSink sink = new CollectingMatchResultSink();
    matchContext.setSink(new DuplicateFilteringResultSink(sink));
    final CompiledPattern compiledPattern = matchContext.getPattern();
    matchContext.setShouldRecursivelyMatch(false);

    PsiElement targetNode = compiledPattern.getTargetNode();
    PsiElement elementToStartMatching = null;

    if (targetNode == null) {
      targetNode = compiledPattern.getNodes().current();
      if (targetNode != null) {
        compiledPattern.getNodes().advance();
        assert !compiledPattern.getNodes().hasNext();
        compiledPattern.getNodes().rewind();

        element = element.getParent();
        if (element == null) {
          return Collections.emptyList();
        }
        while (element.getClass() != targetNode.getClass()) {
          element = element.getParent();
          if (element == null)  return Collections.emptyList();
        }

        elementToStartMatching = element;
      }
    } else {
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
      if (profile == null) return Collections.emptyList();
      targetNode = profile.extendMatchedByDownUp(targetNode);

      final MatchOptions options = matchContext.getOptions();
      MatchingHandler handler = null;
      while (element.getClass() == targetNode.getClass() ||
             compiledPattern.isTypedVar(targetNode) && compiledPattern.getHandler(targetNode).canMatch(targetNode, element, matchContext)) {
        handler = compiledPattern.getHandler(targetNode);
        handler.setPinnedElement(element);
        elementToStartMatching = element;
        if (handler instanceof TopLevelMatchingHandler) break;
        element = element.getParent();
        targetNode = targetNode.getParent();

        if (options.isLooseMatching()) {
          element = profile.updateCurrentNode(element);
          targetNode = profile.updateCurrentNode(targetNode);
        }
      }

      if (!(handler instanceof TopLevelMatchingHandler)) return Collections.emptyList();
    }

    assert targetNode != null : "Could not match down up when no target node";

    visitor.matchContext(newSingleNodeIterator(elementToStartMatching));
    matchContext.getSink().matchingFinished();
    return sink.getMatches();
  }

  private class MatchOnePsiFile extends MatchOneFile {
    private PsiElement file;

    MatchOnePsiFile(PsiElement file) {
      this.file = file;
    }

    @NotNull
    @Override
    protected List<PsiElement> getPsiElementsToProcess() {
      final PsiElement file = this.file;
      this.file = null;
      return new SmartList<>(file);
    }
  }

  private class MatchOneVirtualFile extends MatchOneFile {
    private final VirtualFile myFile;

    MatchOneVirtualFile(VirtualFile file) {
      myFile = file;
    }

    @NotNull
    @Override
    protected List<PsiElement> getPsiElementsToProcess() {
      return ReadAction.compute(
        () -> {
          if (!myFile.isValid()) {
            // file may be been deleted since search started
            return Collections.emptyList();
          }
          final PsiFile file = PsiManager.getInstance(project).findFile(myFile);
          if (file == null) {
            return Collections.emptyList();
          }

          final FileViewProvider viewProvider = file.getViewProvider();
          final List<PsiElement> elementsToProcess = new SmartList<>();

          for (Language lang : viewProvider.getLanguages()) {
            elementsToProcess.add(viewProvider.getPsi(lang));
          }

          return elementsToProcess;
        }
      );
    }
  }

  private abstract class MatchOneFile implements Runnable {
    @Override
    public void run() {
      final List<PsiElement> files = getPsiElementsToProcess();

      final ProgressIndicator progress = matchContext.getSink().getProgressIndicator();
      if (progress != null) progress.setFraction((double)scannedFilesCount/totalFilesToScan);

      ++scannedFilesCount;

      if (files.isEmpty()) return;

      final LanguageFileType fileType = matchContext.getOptions().getFileType();
      final Language patternLanguage = fileType.getLanguage();
      for (final PsiElement file : files) {
        if (file instanceof PsiFile) {
          matchContext.getSink().processFile((PsiFile)file);
        }

        ReadAction.nonBlocking(
          () -> {
            if (!file.isValid()) return;
            final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(file);
            if (profile == null) return;
            match(profile.extendMatchOnePsiFile(file), patternLanguage);
          }
        ).inSmartMode(project).executeSynchronously();
      }
    }

    @NotNull
    protected abstract List<PsiElement> getPsiElementsToProcess();
  }
}
