// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.find.SearchInBackgroundOption;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.platform.util.coroutines.CoroutineScopeKt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.*;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

public class UsageViewManagerImpl extends UsageViewManager {
  private static final Logger LOG = Logger.getInstance(UsageViewManagerImpl.class);
  private final Project project;
  private final @NotNull CoroutineScope coroutineScope;
  private static final Key<UsageView> USAGE_VIEW_KEY = Key.create("USAGE_VIEW");

  @ApiStatus.Internal
  public UsageViewManagerImpl(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    this.project = project;
    this.coroutineScope = coroutineScope;
  }

  @Override
  public @NotNull UsageViewEx createUsageView(UsageTarget @NotNull [] targets,
                                              Usage @NotNull [] usages,
                                              @NotNull UsageViewPresentation presentation,
                                              @Nullable Factory<? extends UsageSearcher> usageSearcherFactory) {
    for (UsageViewFactory factory : UsageViewFactory.EP_NAME.getExtensionList()) {
      UsageViewEx result = factory.createUsageView(targets, usages, presentation, usageSearcherFactory);
      if (result != null) {
        return result;
      }
    }

    UsageViewEx usageView = new UsageViewImpl(project, CoroutineScopeKt.childScope(coroutineScope, "UsageView",
                                                                                   EmptyCoroutineContext.INSTANCE, true), presentation, targets, usageSearcherFactory);
    if (usages.length != 0) {
      usageView.appendUsagesInBulk(Arrays.asList(usages));
      ProgressManager.getInstance().run(new Task.Modal(project, UsageViewBundle.message("progress.title.waiting.for.usages"), false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          usageView.waitForUpdateRequestsCompletion();
        }
      });
    }
    usageView.setSearchInProgress(false);
    return usageView;
  }

  @Override
  public @NotNull UsageView showUsages(UsageTarget @NotNull [] searchedFor,
                                       Usage @NotNull [] foundUsages,
                                       @NotNull UsageViewPresentation presentation,
                                       @Nullable Factory<? extends UsageSearcher> factory) {
    UsageViewEx usageView = createUsageView(searchedFor, foundUsages, presentation, factory);
    showUsageView(usageView, presentation);
    if (usageView instanceof UsageViewImpl) {
      showToolWindow(true);
      UIUtil.invokeLaterIfNeeded(() -> {
        if (!((UsageViewImpl)usageView).isDisposed()) {
          ((UsageViewImpl)usageView).expandRoot();
        }
      });
    }
    return usageView;
  }

  @Override
  public @NotNull UsageView showUsages(UsageTarget @NotNull [] searchedFor,
                                       Usage @NotNull [] foundUsages,
                                       @NotNull UsageViewPresentation presentation) {
    return showUsages(searchedFor, foundUsages, presentation, null);
  }

  void showUsageView(@NotNull UsageViewEx usageView, @NotNull UsageViewPresentation presentation) {
    boolean wasPinned = false;
    Content selectedContent = UsageViewContentManager.getInstance(project).getSelectedContent();
    if (selectedContent != null && System.identityHashCode(selectedContent) == presentation.getRerunHash()) {
      wasPinned = selectedContent.isPinned();
      selectedContent.setPinned(false);//Unpin explicitly to make old content removed as we rerun search
    }
    Content content = UsageViewContentManager.getInstance(project).addContent(
      presentation.getTabText(),
      presentation.getTabName(),
      presentation.getToolwindowTitle(),
      true,
      usageView.getComponent(),
      presentation.isOpenInNewTab() && presentation.getRerunHash() == 0,
      true
    );
    content.setPinned(wasPinned);
    ((UsageViewImpl)usageView).setContent(content);
    content.putUserData(USAGE_VIEW_KEY, usageView);
  }

  @Override
  public @Nullable UsageView searchAndShowUsages(UsageTarget @NotNull [] searchFor,
                                                 @NotNull Supplier<? extends UsageSearcher> searcherFactory,
                                                 boolean showPanelIfOnlyOneUsage,
                                                 boolean showNotFoundMessage,
                                                 @NotNull UsageViewPresentation presentation,
                                                 @Nullable UsageViewStateListener listener) {
    FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation(presentation);
    processPresentation.setShowNotFoundMessage(showNotFoundMessage);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);

    return doSearchAndShow(searchFor, searcherFactory, presentation, processPresentation, listener);
  }

  private UsageView doSearchAndShow(UsageTarget @NotNull [] searchFor,
                                    @NotNull Supplier<? extends UsageSearcher> searcherFactory,
                                    @NotNull UsageViewPresentation presentation,
                                    @NotNull FindUsagesProcessPresentation processPresentation,
                                    @Nullable UsageViewStateListener listener) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      throw new IllegalStateException("Can't start find usages from under write action. Please consider Application.invokeLater() it instead.");
    }
    Supplier<SearchScope> scopeSupplier = getMaxSearchScopeToWarnOfFallingOutOf(searchFor);
    AtomicReference<UsageViewEx> usageViewRef = new AtomicReference<>();
    long start = System.nanoTime();
    AtomicLong firstItemFoundTS = new AtomicLong();
    AtomicBoolean tooManyUsages = new AtomicBoolean();
    Task.Backgroundable task = new Task.Backgroundable(project, getProgressTitle(presentation), true, new SearchInBackgroundOption()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        SearchScope searchScopeToWarnOfFallingOutOf = ReadAction.compute(() -> scopeSupplier.get());
        new SearchForUsagesRunnable(UsageViewManagerImpl.this, UsageViewManagerImpl.this.project, usageViewRef, presentation, searchFor, searcherFactory,
                                    processPresentation, searchScopeToWarnOfFallingOutOf, listener, firstItemFoundTS, tooManyUsages).run();
      }

      @Override
      public void onCancel() {
        reportSearchCompletedToFus(true);
        super.onCancel();
      }

      @Override
      public @NotNull NotificationInfo getNotificationInfo() {
        UsageViewEx usageView = usageViewRef.get();
        int count = usageView == null ? 0 : usageView.getUsagesCount();
        long duration = reportSearchCompletedToFus(false);
        String notification = StringUtil.capitalizeWords(UsageViewBundle.message("usages.n", count), true);
        LOG.debug(notification + " in " + duration + "ms.");
        return new NotificationInfo("Find Usages", UsageViewBundle.message("notification.title.find.usages.finished"), notification);
      }

      private long reportSearchCompletedToFus(boolean isCancelled) {
        long currentTS = System.nanoTime();
        long durationFirstResults = TimeUnit.NANOSECONDS.toMillis(firstItemFoundTS.get() - start);
        long duration = TimeUnit.NANOSECONDS.toMillis(currentTS - start);


        PsiElement element = SearchForUsagesRunnable.getPsiElement(searchFor);
        UsageViewEx view = usageViewRef.get();
        Class<? extends PsiElement> targetClass = element != null ? element.getClass() : null;
        Language language = element != null ? ReadAction.compute(element::getLanguage) : null;
        SearchScope scope = null;
        if (element instanceof DataProvider) {
          scope = UsageView.USAGE_SCOPE.getData((DataProvider)element);
        }
        int numberOfUsagesFound = view == null ? 0 : view.getUsagesCount();

        UsageViewStatisticsCollector.logSearchFinished(myProject, view, targetClass, scope, language, numberOfUsagesFound,
                                                       durationFirstResults, duration,
                                                       tooManyUsages.get(), isCancelled, CodeNavigateSource.FindToolWindow);

        Set<Usage> usages = view == null ? Collections.emptySet() : view.getUsages();
        informRankerMlService(project, usages, FileRankerMlService.CallSource.FIND_USAGES);

        return duration;
      }
    };
    ProgressManager.getInstance().run(task);
    return usageViewRef.get();
  }

  @ApiStatus.Internal
  public static void informRankerMlService(@NotNull Project project,
                                           @NotNull Collection<Usage> usages,
                                           FileRankerMlService.@NotNull CallSource source) {
    FileRankerMlService rankerMlService = FileRankerMlService.getInstance();
    if (rankerMlService == null) {
      return;
    }
    Set<VirtualFile> foundFiles = ContainerUtil.map2SetNotNull(
      usages, usage -> usage instanceof UsageInFile ? ((UsageInFile)usage).getFile() : null
    );
    rankerMlService.onSessionFinished(project, foundFiles, source);
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public @NotNull Supplier<SearchScope> getMaxSearchScopeToWarnOfFallingOutOf(UsageTarget @NotNull [] searchFor) {
    UsageTarget target = searchFor.length > 0 ? searchFor[0] : null;
    DataContext dataContext = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT, sink ->
      DataSink.uiDataSnapshot(sink, target));
    return () -> Objects.requireNonNullElseGet(
      dataContext.getData(UsageView.USAGE_SCOPE),
      // by default, do not warn of falling out of scope
      () -> GlobalSearchScope.everythingScope(project));
  }

  @Override
  public void searchAndShowUsages(UsageTarget @NotNull [] searchFor,
                                  @NotNull Factory<? extends UsageSearcher> searcherFactory,
                                  @NotNull FindUsagesProcessPresentation processPresentation,
                                  @NotNull UsageViewPresentation presentation,
                                  @Nullable UsageViewStateListener listener) {
    doSearchAndShow(searchFor, searcherFactory, presentation, processPresentation, listener);
  }

  @Override
  public UsageView getSelectedUsageView() {
    Content content = UsageViewContentManager.getInstance(project).getSelectedContent();
    if (content != null) {
      return content.getUserData(USAGE_VIEW_KEY);
    }

    return null;
  }

  public static @NotNull @NlsContexts.ProgressTitle String getProgressTitle(@NotNull UsageViewPresentation presentation) {
    return UsageViewBundle.message("search.progress.0.in.1", presentation.getSearchString(), presentation.getScopeText());
  }

  void showToolWindow(boolean activateWindow) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.FIND);
    toolWindow.show(null);
    if (activateWindow && !toolWindow.isActive()) {
      toolWindow.activate(null);
    }
  }


  @ApiStatus.Internal
  public static void showTooManyUsagesWarningLater(@NotNull Project project,
                                                   @NotNull TooManyUsagesStatus tooManyUsagesStatus,
                                                   @NotNull ProgressIndicator indicator,
                                                   @Nullable UsageViewEx usageView,
                                                   @NotNull Supplier<@NlsContexts.DialogMessage String> messageSupplier,
                                                   @Nullable Consumer<? super UsageLimitUtil.Result> onUserClicked) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (usageView != null && usageView.searchHasBeenCancelled() || indicator.isCanceled()) {
        return;
      }
      UsageLimitUtil.Result ret = UsageLimitUtil.showTooManyUsagesWarning(project, messageSupplier.get());
      if (ret == UsageLimitUtil.Result.ABORT) {
        if (usageView != null) {
          usageView.cancelCurrentSearch();
        }
        indicator.cancel();
      }
      tooManyUsagesStatus.userResponded();

      if (onUserClicked != null) {
        onUserClicked.accept(ret);
      }
    });
  }

  @ApiStatus.Internal
  public static long getFileLength(@NotNull VirtualFile virtualFile) {
    return ReadAction.compute(() -> virtualFile.isValid() ? virtualFile.getLength() : -1L);
  }

  @ApiStatus.Internal
  public static @NotNull String presentableSize(long bytes) {
    long megabytes = bytes / (1024 * 1024);
    return UsageViewBundle.message("find.file.size.megabytes", Long.toString(megabytes));
  }

  @ApiStatus.Internal
  public static boolean isInScope(@NotNull Usage usage, @NotNull SearchScope searchScope, @NotNull SearchScope everythingScope) {
    if (searchScope.equals(everythingScope)) return true;
    return ReadAction.compute(() -> {
      VirtualFile file;
      if (usage instanceof PsiElementUsage psiElementUsage) {
        PsiElement element = psiElementUsage.getElement();
        if (element == null) return false;
        if (searchScope instanceof EverythingGlobalScope ||
            searchScope instanceof ProjectScopeImpl ||
            searchScope instanceof ProjectAndLibrariesScope) return true;
        file = PsiUtilCore.getVirtualFile(element);
      }
      else if (usage instanceof UsageInFile usageInFile){
        file = usageInFile.getFile(); 
      }
      else {
        return false;
      }
      return file != null && isFileInScope(file, searchScope);
    });
  }

  private static boolean isFileInScope(@NotNull VirtualFile file, @NotNull SearchScope searchScope) {
    if (file instanceof VirtualFileWindow) {
      file = ((VirtualFileWindow)file).getDelegate();
    }
    file = BackedVirtualFile.getOriginFileIfBacked(file);
    return searchScope.contains(file);
  }

  @ApiStatus.Internal
  public static @Nls(capitalization = Sentence) @NotNull String outOfScopeMessage(int nUsages, @NotNull SearchScope searchScope) {
    return UsageViewBundle.message("0.usages.are.out.of.scope", nUsages, searchScope.getDisplayName());
  }
}
