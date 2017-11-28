/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl;

import com.intellij.find.SearchInBackgroundOption;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author max
 */
public class UsageViewManagerImpl extends UsageViewManager {
  private static final Logger LOG = Logger.getInstance(UsageViewManagerImpl.class);
  private final Project myProject;
  private static final Key<UsageView> USAGE_VIEW_KEY = Key.create("USAGE_VIEW");

  public UsageViewManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public UsageView createUsageView(@NotNull UsageTarget[] targets,
                                   @NotNull Usage[] usages,
                                   @NotNull UsageViewPresentation presentation,
                                   Factory<UsageSearcher> usageSearcherFactory) {
    UsageViewImpl usageView = new UsageViewImpl(myProject, presentation, targets, usageSearcherFactory);
    appendUsages(usages, usageView);
    usageView.setSearchInProgress(false);
    return usageView;
  }

  @Override
  @NotNull
  public UsageView showUsages(@NotNull UsageTarget[] searchedFor,
                              @NotNull Usage[] foundUsages,
                              @NotNull UsageViewPresentation presentation,
                              Factory<UsageSearcher> factory) {
    UsageView usageView = createUsageView(searchedFor, foundUsages, presentation, factory);
    addContent((UsageViewImpl)usageView, presentation);
    showToolWindow(true);
    UIUtil.invokeLaterIfNeeded(() -> {
      if (!((UsageViewImpl)usageView).isDisposed()) {
        ((UsageViewImpl)usageView).expandRoot();
      }
    });
    return usageView;
  }

  @Override
  @NotNull
  public UsageView showUsages(@NotNull UsageTarget[] searchedFor, @NotNull Usage[] foundUsages, @NotNull UsageViewPresentation presentation) {
    return showUsages(searchedFor, foundUsages, presentation, null);
  }

  void addContent(@NotNull UsageViewImpl usageView, @NotNull UsageViewPresentation presentation) {
    Content content = com.intellij.usageView.UsageViewManager.getInstance(myProject).addContent(
      presentation.getTabText(),
      presentation.getTabName(),
      presentation.getToolwindowTitle(),
      true,
      usageView.getComponent(),
      presentation.isOpenInNewTab(),
      true
    );
    usageView.setContent(content);
    content.putUserData(USAGE_VIEW_KEY, usageView);
  }

  @Override
  public UsageView searchAndShowUsages(@NotNull final UsageTarget[] searchFor,
                                       @NotNull final Factory<UsageSearcher> searcherFactory,
                                       final boolean showPanelIfOnlyOneUsage,
                                       final boolean showNotFoundMessage,
                                       @NotNull final UsageViewPresentation presentation,
                                       @Nullable final UsageViewStateListener listener) {
    final FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation(presentation);
    processPresentation.setShowNotFoundMessage(showNotFoundMessage);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);

    return doSearchAndShow(searchFor, searcherFactory, presentation, processPresentation, listener);
  }

  private UsageView doSearchAndShow(@NotNull final UsageTarget[] searchFor,
                                    @NotNull final Factory<UsageSearcher> searcherFactory,
                                    @NotNull final UsageViewPresentation presentation,
                                    @NotNull final FindUsagesProcessPresentation processPresentation,
                                    @Nullable final UsageViewStateListener listener) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      throw new IllegalStateException("Can't start find usages from under write action. Please consider Application.invokeLater() it instead.");
    }
    final SearchScope searchScopeToWarnOfFallingOutOf = getMaxSearchScopeToWarnOfFallingOutOf(searchFor);
    final AtomicReference<UsageViewImpl> usageViewRef = new AtomicReference<>();
    long start = System.currentTimeMillis();
    Task.Backgroundable task = new Task.Backgroundable(myProject, getProgressTitle(presentation), true, new SearchInBackgroundOption()) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        new SearchForUsagesRunnable(UsageViewManagerImpl.this, UsageViewManagerImpl.this.myProject, usageViewRef, presentation, searchFor, searcherFactory,
                                    processPresentation, searchScopeToWarnOfFallingOutOf, listener).run();
      }

      @NotNull
      @Override
      public NotificationInfo getNotificationInfo() {
        UsageViewImpl usageView = usageViewRef.get();
        int count = usageView == null ? 0 : usageView.getUsagesCount();
        String notification = StringUtil.capitalizeWords(UsageViewBundle.message("usages.n", count), true);
        LOG.debug(notification +" in "+(System.currentTimeMillis()-start) +"ms.");
        return new NotificationInfo("Find Usages", "Find Usages Finished", notification);
      }
    };
    ProgressManager.getInstance().run(task);
    return usageViewRef.get();
  }

  @NotNull
  SearchScope getMaxSearchScopeToWarnOfFallingOutOf(@NotNull UsageTarget[] searchFor) {
    UsageTarget target = searchFor.length > 0 ? searchFor[0] : null;
    if (target instanceof TypeSafeDataProvider) {
      final SearchScope[] scope = new SearchScope[1];
      ((TypeSafeDataProvider)target).calcData(UsageView.USAGE_SCOPE, new DataSink() {
        @Override
        public <T> void put(DataKey<T> key, T data) {
          scope[0] = (SearchScope)data;
        }
      });
      return scope[0];
    }
    return GlobalSearchScope.everythingScope(myProject); // by default do not warn of falling out of scope
  }

  @Override
  public void searchAndShowUsages(@NotNull UsageTarget[] searchFor,
                                  @NotNull Factory<UsageSearcher> searcherFactory,
                                  @NotNull FindUsagesProcessPresentation processPresentation,
                                  @NotNull UsageViewPresentation presentation,
                                  @Nullable UsageViewStateListener listener) {
    doSearchAndShow(searchFor, searcherFactory, presentation, processPresentation, listener);
  }

  @Override
  public UsageView getSelectedUsageView() {
    final Content content = com.intellij.usageView.UsageViewManager.getInstance(myProject).getSelectedContent();
    if (content != null) {
      return content.getUserData(USAGE_VIEW_KEY);
    }

    return null;
  }

  @NotNull
  public static String getProgressTitle(@NotNull UsageViewPresentation presentation) {
    final String scopeText = presentation.getScopeText();
    String usagesString = StringUtil.capitalize(presentation.getUsagesString());
    return UsageViewBundle.message("progress.searching.for.in", usagesString, scopeText, presentation.getContextText());
  }

  void showToolWindow(boolean activateWindow) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
    toolWindow.show(null);
    if (activateWindow && !toolWindow.isActive()) {
      toolWindow.activate(null);
    }
  }

  protected static void appendUsages(@NotNull final Usage[] foundUsages, @NotNull final UsageViewImpl usageView) {
    ApplicationManager.getApplication().runReadAction(() -> {
      for (Usage foundUsage : foundUsages) {
        usageView.appendUsage(foundUsage);
      }
    });
  }


  public static void showTooManyUsagesWarningLater(@NotNull final Project project,
                                                   @NotNull final TooManyUsagesStatus tooManyUsagesStatus,
                                                   @NotNull final ProgressIndicator indicator,
                                                   @NotNull final UsageViewPresentation presentation,
                                                   final int usageCount,
                                                   @Nullable final UsageViewImpl usageView) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (usageView != null && usageView.searchHasBeenCancelled() || indicator.isCanceled()) return;
      int shownUsageCount = usageView == null ? usageCount : usageView.getRoot().getRecursiveUsageCount();
      String message = UsageViewBundle.message("find.excessive.usage.count.prompt", shownUsageCount, StringUtil.pluralize(presentation.getUsagesWord()));
      UsageLimitUtil.Result ret = UsageLimitUtil.showTooManyUsagesWarning(project, message, presentation);
      if (ret == UsageLimitUtil.Result.ABORT) {
        if (usageView != null) {
          usageView.cancelCurrentSearch();
        }
        indicator.cancel();
      }
      tooManyUsagesStatus.userResponded();
    });
  }

  public static long getFileLength(@NotNull final VirtualFile virtualFile) {
    final long[] length = {-1L};
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!virtualFile.isValid()) return;
      length[0] = virtualFile.getLength();
    });
    return length[0];
  }

  @NotNull
  public static String presentableSize(long bytes) {
    long megabytes = bytes / (1024 * 1024);
    return UsageViewBundle.message("find.file.size.megabytes", Long.toString(megabytes));
  }

  public static boolean isInScope(@NotNull Usage usage, @NotNull SearchScope searchScope) {
    PsiElement element = null;
    VirtualFile file = usage instanceof UsageInFile ? ((UsageInFile)usage).getFile() :
                       usage instanceof PsiElementUsage
                       ? PsiUtilCore.getVirtualFile(element = ((PsiElementUsage)usage).getElement())
                       : null;
    if (file != null) {
      return isFileInScope(file, searchScope);
    }
    return element != null &&
           (searchScope instanceof EverythingGlobalScope ||
            searchScope instanceof ProjectScopeImpl ||
            searchScope instanceof ProjectAndLibrariesScope);
  }

  private static boolean isFileInScope(@NotNull VirtualFile file, @NotNull SearchScope searchScope) {
    if (file instanceof VirtualFileWindow) {
      file = ((VirtualFileWindow)file).getDelegate();
    }
    return searchScope.contains(file);
  }

  @NotNull
  public static String outOfScopeMessage(int nUsages, @NotNull SearchScope searchScope) {
    return (nUsages == 1 ? "One usage is" : nUsages + " usages are") +
           " out of scope '"+ searchScope.getDisplayName()+"'";
  }

}
