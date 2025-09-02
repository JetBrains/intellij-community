// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.NlsContexts.Command;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.RefactoringListenerManagerImpl;
import com.intellij.refactoring.listeners.impl.RefactoringTransaction;
import com.intellij.refactoring.suggested.SuggestedRefactoringProvider;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.usages.impl.UnknownUsagesInUnloadedModules;
import com.intellij.usages.impl.UsageViewEx;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.SlowOperations;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.*;

public abstract class BaseRefactoringProcessor implements Runnable {
  private static final Logger LOG = Logger.getInstance(BaseRefactoringProcessor.class);
  private static boolean PREVIEW_IN_TESTS = true;

  protected final Project myProject;
  protected final @NotNull SearchScope myRefactoringScope;

  private RefactoringTransaction myTransaction;
  private boolean myIsPreviewUsages;
  protected Runnable myPrepareSuccessfulSwingThreadCallback;
  private UsageView myUsageView;

  protected BaseRefactoringProcessor(@NotNull Project project) {
    this(project, null);
  }

  protected BaseRefactoringProcessor(@NotNull Project project, @Nullable Runnable prepareSuccessfulCallback) {
    this(project, GlobalSearchScope.projectScope(project), prepareSuccessfulCallback);
  }

  protected BaseRefactoringProcessor(@NotNull Project project,
                                     @NotNull SearchScope refactoringScope,
                                     @Nullable Runnable prepareSuccessfulCallback) {
    myProject = project;
    myPrepareSuccessfulSwingThreadCallback = prepareSuccessfulCallback;
    myRefactoringScope = refactoringScope;
  }

  @ApiStatus.Internal
  public Runnable getPrepareSuccessfulSwingThreadCallback() {
    return myPrepareSuccessfulSwingThreadCallback;
  }

  protected abstract @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages);

  /**
   * Is called inside atomic action.
   */
  @ApiStatus.OverrideOnly
  protected abstract UsageInfo @NotNull [] findUsages();

  /**
   * is called when usage search is re-run.
   *
   * @param elements - refreshed elements that are returned by UsageViewDescriptor.getElements()
   */
  protected void refreshElements(PsiElement @NotNull [] elements) {}

  /**
   * Is called inside atomic action.
   *
   * @param refUsages usages to be filtered
   * @return true if preprocessed successfully
   */
  @ApiStatus.OverrideOnly
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    prepareSuccessful();
    return true;
  }

  /**
   * Is called inside atomic action.
   */
  @ApiStatus.OverrideOnly
  protected boolean isPreviewUsages(UsageInfo @NotNull [] usages) {
    return myIsPreviewUsages;
  }

  @ApiStatus.Internal
  protected boolean isPreviewUsages() {
    return myIsPreviewUsages;
  }

  protected Set<UnloadedModuleDescription> computeUnloadedModulesFromUseScope(UsageViewDescriptor descriptor) {
    if (ModuleManager.getInstance(myProject).getUnloadedModuleDescriptions().isEmpty()) {
      //optimization
      return Collections.emptySet();
    }

    Set<UnloadedModuleDescription> unloadedModulesInUseScope = new LinkedHashSet<>();
    for (PsiElement element : descriptor.getElements()) {
      SearchScope useScope = element.getUseScope();
      if (useScope instanceof GlobalSearchScope) {
        unloadedModulesInUseScope.addAll(((GlobalSearchScope)useScope).getUnloadedModulesBelongingToScope());
      }
    }
    return unloadedModulesInUseScope;
  }


  public void setPreviewUsages(boolean isPreviewUsages) {
    myIsPreviewUsages = isPreviewUsages;
  }

  public void setPrepareSuccessfulSwingThreadCallback(Runnable prepareSuccessfulSwingThreadCallback) {
    myPrepareSuccessfulSwingThreadCallback = prepareSuccessfulSwingThreadCallback;
  }

  protected RefactoringTransaction getTransaction() {
    return myTransaction;
  }

  /**
   * Is called in a command and inside atomic action.
   * <p>
   * It is called by {@link #doRefactoring}.
   */
  protected abstract void performRefactoring(UsageInfo @NotNull [] usages);

  protected abstract @NotNull @Command String getCommandName();

  /**
   * Called as part of {@link #run}.
   * <p>
   * Must be called on EDT and outside a write action.
   */
  @RequiresEdt
  protected void doRun() {
    if (!PsiDocumentManager.getInstance(myProject).commitAllDocumentsUnderProgress()) {
      return;
    }
    final Ref<UsageInfo[]> refUsages = new Ref<>();
    final Ref<Language> refErrorLanguage = new Ref<>();
    final Ref<Boolean> refProcessCanceled = new Ref<>();
    final Ref<Boolean> anyException = new Ref<>();
    final Ref<Boolean> indexNotReadyException = new Ref<>();

    DumbService.getInstance(myProject).completeJustSubmittedTasks();

    final Runnable findUsagesRunnable = () -> {
      try {
        refUsages.set(ReadAction.compute(this::findUsages));
      }
      catch (UnknownReferenceTypeException e) {
        refErrorLanguage.set(e.getElementLanguage());
      }
      catch (ProcessCanceledException e) {
        refProcessCanceled.set(Boolean.TRUE);
      }
      catch (IndexNotReadyException e) {
        indexNotReadyException.set(Boolean.TRUE);
      }
      catch (Throwable e) {
        anyException.set(Boolean.TRUE);
        LOG.error(e);
      }
    };

    long findUsagesStart = System.currentTimeMillis();
    boolean isProgressFinished = ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(findUsagesRunnable, RefactoringBundle.message("progress.text"), true, myProject);
    long findUsagesDuration = System.currentTimeMillis() - findUsagesStart;
    RefactoringUsageCollector.USAGES_SEARCHED.log(this.getClass(), !isProgressFinished, findUsagesDuration);
    if (!isProgressFinished) {
      return;
    }

    if (!refErrorLanguage.isNull()) {
      MessagesService.getInstance().showErrorDialog(myProject, RefactoringBundle.message("unsupported.refs.found", refErrorLanguage.get().getDisplayName()), RefactoringBundle.message("error.title"));
      return;
    }
    if (!indexNotReadyException.isNull() || DumbService.isDumb(myProject)) {
      DumbService.getInstance(myProject).showDumbModeNotificationForFunctionality(RefactoringBundle.message("refactoring.dumb.mode.notification"),
                                                                                  DumbModeBlockedFunctionality.Refactoring);
      return;
    }
    if (!refProcessCanceled.isNull()) {
      MessagesService.getInstance().showErrorDialog(myProject, RefactoringBundle.message("refactoring.index.corruption.notifiction"), RefactoringBundle.message("error.title"));
      return;
    }

    if (!anyException.isNull()) {
      //do not proceed if find usages fails
      return;
    }
    assert !refUsages.isNull(): "Null usages from processor " + this;
    if (!preprocessUsages(refUsages)) return;
    final UsageInfo[] usages = refUsages.get();
    assert usages != null;
    UsageViewDescriptor descriptor = createUsageViewDescriptor(usages);
    boolean isPreview = isPreviewUsages(usages) || !computeUnloadedModulesFromUseScope(descriptor).isEmpty();
    if (!isPreview) {
      isPreview = !ensureElementsWritable(usages, descriptor) || UsageViewUtil.hasReadOnlyUsages(usages);
      if (isPreview) {
        RefactoringUiService.getInstance().setStatusBarInfo(myProject, RefactoringBundle.message("readonly.occurences.found"));
      }
    }
    if (isPreview) {
      for (UsageInfo usage : usages) {
        LOG.assertTrue(usage != null, getClass());
      }
      previewRefactoring(usages);
    }
    else {
      execute(usages);
    }
  }

  @TestOnly
  public static <T extends Throwable> void runWithDisabledPreview(ThrowableRunnable<T> runnable) throws T {
    PREVIEW_IN_TESTS = false;
    try {
      runnable.run();
    }
    finally {
      PREVIEW_IN_TESTS = true;
    }
  }

  protected void previewRefactoring(UsageInfo @NotNull [] usages) {
    if (ApplicationManager.getApplication().isUnitTestMode() || Boolean.getBoolean("ide.performance.skip.refactoring.dialogs")) {
      if (!PREVIEW_IN_TESTS) {
        throw new RuntimeException("Unexpected preview in tests: " + StringUtil.join(usages, UsageInfo::toString, ", "));
      }
      ensureElementsWritable(usages, createUsageViewDescriptor(usages));
      execute(usages);
      return;
    }
    final UsageViewDescriptor viewDescriptor = createUsageViewDescriptor(usages);
    final PsiElement[] elements = viewDescriptor.getElements();
    final PsiElement2UsageTargetAdapter[] targets = PsiElement2UsageTargetAdapter.convert(elements, true);
    Factory<UsageSearcher> factory = () -> new UsageInfoSearcherAdapter() {
      @Override
      public void generate(final @NotNull Processor<? super Usage> processor) {
        ApplicationManager.getApplication().runReadAction(() -> {
          for (int i = 0; i < elements.length; i++) {
            elements[i] = targets[i].getElement();
          }
          refreshElements(elements);
        });
        processUsages(processor, myProject);
      }

      @Override
      protected UsageInfo @NotNull [] findUsages() {
        return BaseRefactoringProcessor.this.findUsages();
      }
    };

    showUsageView(viewDescriptor, factory, usages);
  }

  protected boolean skipNonCodeUsages() {
    return false;
  }

  private boolean ensureElementsWritable(UsageInfo @NotNull [] usages, @NotNull UsageViewDescriptor descriptor) {
    // protect against poorly implemented equality
    Set<PsiElement> elements = new ReferenceOpenHashSet<>();
    for (UsageInfo usage : usages) {
      assert usage != null: "Found null element in usages array";
      if (skipNonCodeUsages() && usage.isNonCodeUsage()) {
        continue;
      }
      PsiElement element = usage.getElement();
      if (element != null) {
        elements.add(element);
      }
    }
    elements.addAll(getElementsToWrite(descriptor));
    return ensureFilesWritable(myProject, elements);
  }

  private static boolean ensureFilesWritable(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements) {
    PsiElement[] psiElements = PsiUtilCore.toPsiElementArray(elements);
    return CommonRefactoringUtil.checkReadOnlyStatus(project, psiElements);
  }

  public void executeEx(final UsageInfo @NotNull [] usages) {
    execute(usages);
  }

  @ApiStatus.OverrideOnly
  protected void execute(final UsageInfo @NotNull [] usages) {
    long executeStart = System.currentTimeMillis();
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      Collection<UsageInfo> usageInfos = new LinkedHashSet<>(Arrays.asList(usages));
      doRefactoring(usageInfos);
      if (isGlobalUndoAction()) CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
      SuggestedRefactoringProvider.getInstance(myProject).reset();
    }, getCommandName(), null, getUndoConfirmationPolicy());
    long executeDuration = System.currentTimeMillis() - executeStart;
    RefactoringUsageCollector.EXECUTED.log(this.getClass(), executeDuration);
  }

  protected boolean isGlobalUndoAction() {
    return CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext()) == null;
  }

  protected @NotNull UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return UndoConfirmationPolicy.DEFAULT;
  }

  private static @NotNull UsageViewPresentation createPresentation(@NotNull UsageViewDescriptor descriptor, Usage @NotNull [] usages) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText(RefactoringBundle.message("usageView.tabText"));
    presentation.setTargetsNodeText(descriptor.getProcessedElementsHeader());
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));
    int codeUsageCount = 0;
    int nonCodeUsageCount = 0;
    int dynamicUsagesCount = 0;
    Set<PsiFile> codeFiles = new HashSet<>();
    Set<PsiFile> nonCodeFiles = new HashSet<>();
    Set<PsiFile> dynamicUsagesCodeFiles = new HashSet<>();

    for (Usage usage : usages) {
      if (usage instanceof PsiElementUsage elementUsage) {
        final PsiElement element = elementUsage.getElement();
        if (element == null) continue;
        final PsiFile containingFile = element.getContainingFile();
        if (usage instanceof UsageInfo2UsageAdapter && ((UsageInfo2UsageAdapter)usage).getUsageInfo().isDynamicUsage()) {
          dynamicUsagesCount++;
          dynamicUsagesCodeFiles.add(containingFile);
        }
        else if (elementUsage.isNonCodeUsage()) {
          nonCodeUsageCount++;
          nonCodeFiles.add(containingFile);
        }
        else {
          codeUsageCount++;
          codeFiles.add(containingFile);
        }
      }
    }
    codeFiles.remove(null);
    nonCodeFiles.remove(null);
    dynamicUsagesCodeFiles.remove(null);

    presentation.setCodeUsagesString(UsageViewBundle.message(
      "usage.view.results.node.prefix",
      UsageViewBundle.message("usage.view.results.node.code"),
      descriptor.getCodeReferencesText(codeUsageCount, codeFiles.size())
    ));
    presentation.setNonCodeUsagesString(UsageViewBundle.message(
      "usage.view.results.node.prefix",
      UsageViewBundle.message("usage.view.results.node.non.code"),
      descriptor.getCommentReferencesText(nonCodeUsageCount, nonCodeFiles.size())
    ));
    presentation.setDynamicUsagesString(UsageViewBundle.message(
      "usage.view.results.node.prefix",
      UsageViewBundle.message("usage.view.results.node.dynamic"),
      descriptor.getCodeReferencesText(dynamicUsagesCount, dynamicUsagesCodeFiles.size())
    ));
    return presentation;
  }

  /**
   * Processes conflicts (possibly shows UI). In case we're running in unit test mode this method will
   * throw {@link BaseRefactoringProcessor.ConflictsInTestsException} that can be handled inside a test.
   * Thrown exception would contain conflicts' messages.
   *
   * @param project   project
   * @param conflicts map with conflict messages and locations
   * @return true if refactoring could proceed or false if refactoring should be cancelled
   */
  public static boolean processConflicts(@NotNull Project project, @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    if (conflicts.isEmpty()) return true;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore()) return true;
      throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
    }

    ConflictsDialogBase conflictsDialog = RefactoringUiService.getInstance().createConflictsDialog(project, conflicts, null, true, true);
    return conflictsDialog.showAndGet();
  }

  private void showUsageView(@NotNull UsageViewDescriptor viewDescriptor,
                             @NotNull Factory<? extends UsageSearcher> factory,
                             UsageInfo @NotNull [] usageInfos) {
    UsageViewManager viewManager = UsageViewManager.getInstance(myProject);

    final PsiElement[] initialElements = viewDescriptor.getElements();
    final UsageTarget[] targets = PsiElement2UsageTargetAdapter.convert(initialElements, true);
    final Ref<Usage[]> convertUsagesRef = new Ref<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ApplicationManager.getApplication().runReadAction(
        () -> convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos))),
      RefactoringBundle.message("refactoring.preprocess.usages.progress"), true, myProject)) return;

    if (convertUsagesRef.isNull()) return;

    final Usage[] usages = convertUsagesRef.get();

    final UsageViewPresentation presentation = createPresentation(viewDescriptor, usages);
    if (myUsageView == null) {
      myUsageView = viewManager.showUsages(targets, usages, presentation, factory);
      customizeUsagesView(viewDescriptor, myUsageView);
    } else {
      myUsageView.removeUsagesBulk(myUsageView.getUsages());
      ((UsageViewEx)myUsageView).appendUsagesInBulk(Arrays.asList(usages));
    }
    Set<UnloadedModuleDescription> unloadedModules = computeUnloadedModulesFromUseScope(viewDescriptor);
    if (!unloadedModules.isEmpty()) {
      myUsageView.appendUsage(new UnknownUsagesInUnloadedModules(unloadedModules));
    }
  }

  protected void customizeUsagesView(final @NotNull UsageViewDescriptor viewDescriptor, final @NotNull UsageView usageView) {
    Runnable refactoringRunnable = () -> {
      Set<UsageInfo> usagesToRefactor = UsageViewUtil.getNotExcludedUsageInfos(usageView);
      final UsageInfo[] infos = usagesToRefactor.toArray(UsageInfo.EMPTY_ARRAY);
      if (ensureElementsWritable(infos, viewDescriptor)) {
        execute(infos);
      }
    };

    String canNotMakeString = RefactoringBundle.message("usageView.need.reRun");

    addDoRefactoringAction(usageView, refactoringRunnable, canNotMakeString);
    usageView.setRerunAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        run();
      }
    });
  }

  private void addDoRefactoringAction(@NotNull UsageView usageView, @NotNull Runnable refactoringRunnable, @NotNull String canNotMakeString) {
    usageView.addPerformOperationAction(refactoringRunnable, getCommandName(), canNotMakeString,
                                        RefactoringBundle.message("usageView.doAction"), false);
  }

  private void doRefactoring(final @NotNull Collection<UsageInfo> usageInfoSet) {
   for (Iterator<UsageInfo> iterator = usageInfoSet.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      final PsiElement element = usageInfo.getElement();
      if (element == null || !isToBeChanged(usageInfo)) {
        iterator.remove();
      }
    }

    String commandName = getCommandName();
    LocalHistoryAction action = LocalHistory.getInstance().startAction(commandName);

    final UsageInfo[] writableUsageInfos = usageInfoSet.toArray(UsageInfo.EMPTY_ARRAY);
    final String refactoringId = getRefactoringId();
    try {
      if (refactoringId != null) {
        RefactoringEventData data = getBeforeData();
        if (data != null) {
          data.addUsages(Arrays.asList(writableUsageInfos));
        }
        myProject.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(refactoringId, data);
      }

      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      RefactoringListenerManagerImpl listenerManager = (RefactoringListenerManagerImpl)RefactoringListenerManager.getInstance(myProject);
      myTransaction = listenerManager.startTransaction();
      final Map<RefactoringHelper, Object> preparedData = new LinkedHashMap<>();
      final Runnable prepareHelpersRunnable = () -> {
        RefactoringEventData data = ReadAction.compute(() -> getBeforeData());
        PsiElement[] elements = data != null ? data.getUserData(RefactoringEventData.PSI_ELEMENT_ARRAY_KEY) : null;
        PsiElement primaryElement = data != null ? data.getUserData(RefactoringEventData.PSI_ELEMENT_KEY) : null;
        PsiElement[] allElements = elements != null ? ArrayUtil.append(elements, primaryElement) : new PsiElement[]{primaryElement};
        for (final RefactoringHelper<?> helper : RefactoringHelper.EP_NAME.getExtensionList()) {
          Object operation = ReadAction.compute(() -> {
            return helper.prepareOperation(writableUsageInfos, ContainerUtil.filter(allElements, e -> e != null));
          });
          preparedData.put(helper, operation);
        }
      };

      ProgressManager.getInstance().runProcessWithProgressSynchronously(prepareHelpersRunnable,
                                                                        RefactoringBundle.message("refactoring.prepare.progress"), false, myProject);

      if (refactoringId != null) {
        UndoManager.getInstance(myProject).undoableActionPerformed(new UndoRefactoringAction(myProject, refactoringId));
      }

      ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      if (!app.runWriteActionWithCancellableProgressInDispatchThread(commandName, myProject, null,
                                                                     indicator -> performRefactoring(writableUsageInfos))) {
        return;
      }

      DumbService.getInstance(myProject).completeJustSubmittedTasks();

      // Execute refactoring helpers (for example, optimizing imports)
      for (Map.Entry<RefactoringHelper, Object> e : preparedData.entrySet()) {
        final RefactoringHelper refactoringHelper = e.getKey();
        final Object operation = e.getValue();
        //noinspection unchecked
        refactoringHelper.performOperation(myProject, operation);
      }
      myTransaction.commit();
      if (!app.runWriteActionWithCancellableProgressInDispatchThread(commandName, myProject, null,
                                                                     indicator -> performPsiSpoilingRefactoring())) {
        return;
      }
    }
    finally {
      if (refactoringId != null) {
        myProject.getMessageBus()
          .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(refactoringId, getAfterData(writableUsageInfos));
      }
      action.finish();
      myUsageView = null;
    }

    int count = writableUsageInfos.length;
    if (count > 0) {
      RefactoringUiService.getInstance().setStatusBarInfo(myProject, RefactoringBundle.message("statusBar.refactoring.result", count));
    }
    else {
      if (!isPreviewUsages(writableUsageInfos)) {
        RefactoringUiService.getInstance().setStatusBarInfo(myProject, RefactoringBundle.message("statusBar.noUsages"));
      }
    }
  }

  protected boolean isToBeChanged(@NotNull UsageInfo usageInfo) {
    return usageInfo.isWritable();
  }

  /**
   * Refactorings that spoil PSI (write something directly to documents etc.) should
   * do that in this method.<br>
   * This method is called immediately after
   * <code>{@link #performRefactoring(UsageInfo[])}</code>.
   */
  protected void performPsiSpoilingRefactoring() {
  }

  protected void prepareSuccessful() {
    if (myPrepareSuccessfulSwingThreadCallback != null) {
      // make sure that dialog is closed in swing thread
      try {
        ApplicationManager.getApplication().invokeAndWait(myPrepareSuccessfulSwingThreadCallback);
      }
      catch (RuntimeException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public final void run() {
    Runnable baseRunnable = () -> {
      try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
        doRun();
      }
    };
    Runnable runnable = shouldDisableAccessChecks() ?
                        () -> NonProjectFileWritingAccessProvider.disableChecksDuring(baseRunnable) :
                        baseRunnable;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ThreadingAssertions.assertWriteIntentReadAccess();
      runnable.run();
      return;
    }
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      LOG.error("Refactorings should not be started inside write action\n because they start progress inside and any read action from the progress task would cause the deadlock", new Exception());
      DumbService.getInstance(myProject).smartInvokeLater(runnable);
    }
    else {
      runnable.run();
    }
  }

  protected boolean shouldDisableAccessChecks() {
    return false;
  }

  public static final class ConflictsInTestsException extends RuntimeException {
    private final Collection<String> messages;

    private static boolean myTestIgnore;

    public ConflictsInTestsException(@NotNull Collection<String> messages) {
      this.messages = messages;
    }

    public static boolean isTestIgnore() {
      return myTestIgnore;
    }

    @TestOnly
    public static <T extends Throwable> void withIgnoredConflicts(@NotNull ThrowableRunnable<T> r) throws T {
      try {
        myTestIgnore = true;
        r.run();
      }
      finally {
        myTestIgnore = false;
      }
    }

    public @NotNull Collection<String> getMessages() {
        List<String> result = new ArrayList<>(messages);
        for (int i = 0; i < messages.size(); i++) {
          result.set(i, result.get(i).replaceAll("<[^>]+>", ""));
        }
        return result;
      }

    @Override
    public String getMessage() {
      List<String> result = ContainerUtil.sorted(messages);
      return StringUtil.join(result, "\n");
    }
  }

  protected boolean showConflicts(@NotNull MultiMap<PsiElement, @DialogMessage String> conflicts, UsageInfo @Nullable [] usages) {
    if (!conflicts.isEmpty() && (ApplicationManager.getApplication().isUnitTestMode()
                                 || Boolean.getBoolean("ide.performance.skip.refactoring.dialogs"))) {
      if (!ConflictsInTestsException.isTestIgnore()) throw new ConflictsInTestsException(conflicts.values());
      return true;
    }

    if (myPrepareSuccessfulSwingThreadCallback != null && !conflicts.isEmpty()) {
      final String refactoringId = getRefactoringId();
      if (refactoringId != null) {
        RefactoringEventData conflictUsages = new RefactoringEventData();
        conflictUsages.putUserData(RefactoringEventData.CONFLICTS_KEY, conflicts.values());
        myProject.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
          .conflictsDetected(refactoringId, conflictUsages);
      }
      final ConflictsDialogBase conflictsDialog = prepareConflictsDialog(conflicts, usages);
      if (!conflictsDialog.showAndGet()) {
        if (conflictsDialog.isShowConflicts()) prepareSuccessful();
        return false;
      }
    }

    prepareSuccessful();
    return true;
  }

  protected @NotNull ConflictsDialogBase prepareConflictsDialog(@NotNull MultiMap<PsiElement, @DialogMessage String> conflicts, UsageInfo @Nullable [] usages) {
    final ConflictsDialogBase conflictsDialog = createConflictsDialog(conflicts, usages);
    conflictsDialog.setCommandName(getCommandName());
    return conflictsDialog;
  }

  protected @Nullable RefactoringEventData getBeforeData() {
    return null;
  }

  protected @Nullable RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    return null;
  }

  protected @NonNls @Nullable String getRefactoringId() {
    return null;
  }

  protected @NotNull ConflictsDialogBase createConflictsDialog(@NotNull MultiMap<PsiElement, @DialogMessage String> conflicts, UsageInfo @Nullable [] usages) {
    return RefactoringUiService.getInstance().createConflictsDialog(myProject, conflicts, usages == null ? null : () -> execute(usages), false, true);
  }

  protected @NotNull Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
    return Arrays.asList(descriptor.getElements());
  }

  public static class UnknownReferenceTypeException extends RuntimeException {
    private final Language myElementLanguage;

    public UnknownReferenceTypeException(@NotNull Language elementLanguage) {
      myElementLanguage = elementLanguage;
    }

    @NotNull
    Language getElementLanguage() {
      return myElementLanguage;
    }
  }

  private static class UndoRefactoringAction extends BasicUndoableAction {
    private final Project myProject;
    private final String myRefactoringId;

    UndoRefactoringAction(@NotNull Project project, @NotNull String refactoringId) {
      myProject = project;
      myRefactoringId = refactoringId;
    }

    @Override
    public void undo() {
      myProject.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).undoRefactoring(myRefactoringId);
    }

    @Override
    public void redo() {
      myProject.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).redoRefactoring(myRefactoringId);
    }
  }
}
