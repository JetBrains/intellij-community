/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.find.FindManager;
import com.intellij.find.SearchInBackgroundOption;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.ui.RangeBlinker;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author max
 */
public class UsageViewManagerImpl extends UsageViewManager {
  private final Project myProject;
  private static final Key<UsageView> USAGE_VIEW_KEY = Key.create("USAGE_VIEW");
  @NonNls private static final String LARGE_FILES_HREF_TARGET = "LargeFiles";
  @NonNls private static final String FIND_OPTIONS_HREF_TARGET = "FindOptions";

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
    return usageView;
  }

  @Override
  @NotNull
  public UsageView showUsages(@NotNull UsageTarget[] searchedFor, @NotNull Usage[] foundUsages, @NotNull UsageViewPresentation presentation) {
    return showUsages(searchedFor, foundUsages, presentation, null);
  }

  private void addContent(@NotNull UsageViewImpl usageView, @NotNull UsageViewPresentation presentation) {
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
    final AtomicReference<UsageViewImpl> usageViewRef = new AtomicReference<UsageViewImpl>();

    final FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation();
    processPresentation.setShowNotFoundMessage(showNotFoundMessage);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);

    Task.Backgroundable task = new Task.Backgroundable(myProject, getProgressTitle(presentation), true, new SearchInBackgroundOption()) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        new SearchForUsagesRunnable(UsageViewManagerImpl.this.myProject, usageViewRef, presentation, searchFor, searcherFactory,
                                    processPresentation, listener).run();
      }

      @Override
      public DumbModeAction getDumbModeAction() {
        return DumbModeAction.CANCEL;
      }

      @Override
      @Nullable
      public NotificationInfo getNotificationInfo() {
        String notification = usageViewRef.get() != null ? usageViewRef.get().getUsagesCount() + " Usage(s) Found" : "No Usages Found";
        return new NotificationInfo("Find Usages", "Find Usages Finished", notification);
      }
    };
    ProgressManager.getInstance().run(task);
    return usageViewRef.get();
  }

  @Override
  public void searchAndShowUsages(@NotNull UsageTarget[] searchFor,
                                  @NotNull Factory<UsageSearcher> searcherFactory,
                                  @NotNull FindUsagesProcessPresentation processPresentation,
                                  @NotNull UsageViewPresentation presentation,
                                  @Nullable UsageViewStateListener listener) {
    final AtomicReference<UsageViewImpl> usageView = new AtomicReference<UsageViewImpl>();
    final SearchForUsagesRunnable runnable = new SearchForUsagesRunnable(myProject, usageView, presentation, searchFor, searcherFactory, processPresentation, listener);
    final Factory<ProgressIndicator> progressIndicatorFactory = processPresentation.getProgressIndicatorFactory();

    final ProgressIndicator progressIndicator = progressIndicatorFactory != null ? progressIndicatorFactory.create() : null;

    final AtomicBoolean findUsagesStartedShown = new AtomicBoolean();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          ProgressManager.getInstance().runProcess(new Runnable() {
            @Override
            public void run() {
              runnable.searchUsages(findUsagesStartedShown);
            }
          }, progressIndicator);
        }
        catch (ProcessCanceledException e) {
          //ignore
        }
        finally {
          runnable.endSearchForUsages(findUsagesStartedShown);
        }
      }
    });
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
    if (scopeText == null) {
      return UsageViewBundle.message("progress.searching.for", StringUtil.capitalize(presentation.getUsagesString()));
    }
    return UsageViewBundle.message("progress.searching.for.in", StringUtil.capitalize(presentation.getUsagesString()), scopeText);
  }

  private void showToolWindow(boolean activateWindow) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
    toolWindow.show(null);
    if (activateWindow && !toolWindow.isActive()) {
      toolWindow.activate(null);
    }
  }

  private static void appendUsages(@NotNull final Usage[] foundUsages, @NotNull final UsageViewImpl usageView) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        for (Usage foundUsage : foundUsages) {
          usageView.appendUsage(foundUsage);
        }
      }
    });
  }


  public static void showTooManyUsagesWarning(@NotNull final Project project,
                                              @NotNull final TooManyUsagesStatus tooManyUsagesStatus,
                                              @NotNull final ProgressIndicator indicator,
                                              final int usageCount,
                                              final UsageViewImpl usageView) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (usageView != null && usageView.searchHasBeenCancelled() || indicator.isCanceled()) return;
        String message = UsageViewBundle.message("find.excessive.usage.count.prompt", usageCount);
        UsageLimitUtil.Result ret = UsageLimitUtil.showTooManyUsagesWarning(project, message);
        if (ret == UsageLimitUtil.Result.ABORT && usageView != null) {
          usageView.cancelCurrentSearch();
          indicator.cancel();
        }
        tooManyUsagesStatus.userResponded();
      }
    });
  }

  private class SearchForUsagesRunnable implements Runnable {
    private final AtomicInteger myUsageCountWithoutDefinition = new AtomicInteger(0);
    private final AtomicReference<Usage> myFirstUsage = new AtomicReference<Usage>();
    @NotNull private final Project myProject;
    private final AtomicReference<UsageViewImpl> myUsageViewRef;
    private final UsageViewPresentation myPresentation;
    private final UsageTarget[] mySearchFor;
    private final Factory<UsageSearcher> mySearcherFactory;
    private final FindUsagesProcessPresentation myProcessPresentation;
    private final UsageViewStateListener myListener;

    private SearchForUsagesRunnable(@NotNull Project project,
                                    @NotNull AtomicReference<UsageViewImpl> usageViewRef,
                                    @NotNull UsageViewPresentation presentation,
                                    @NotNull UsageTarget[] searchFor,
                                    @NotNull Factory<UsageSearcher> searcherFactory,
                                    @NotNull FindUsagesProcessPresentation processPresentation,
                                    @Nullable UsageViewStateListener listener) {
      myProject = project;
      myUsageViewRef = usageViewRef;
      myPresentation = presentation;
      mySearchFor = searchFor;
      mySearcherFactory = searcherFactory;
      myProcessPresentation = processPresentation;
      myListener = listener;
    }

    private UsageViewImpl getUsageView(ProgressIndicator indicator) {
      UsageViewImpl usageView = myUsageViewRef.get();
      if (usageView != null) return usageView;
      int usageCount = myUsageCountWithoutDefinition.get();
      if (usageCount >= 2 || usageCount == 1 && myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
        usageView = new UsageViewImpl(myProject, myPresentation, mySearchFor, mySearcherFactory);
        usageView.associateProgress(indicator);
        if (myUsageViewRef.compareAndSet(null, usageView)) {
          openView(usageView);
          final Usage firstUsage = myFirstUsage.get();
          if (firstUsage != null) {
            final UsageViewImpl finalUsageView = usageView;
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                finalUsageView.appendUsage(firstUsage);
              }
            });
          }
        }
        else {
          Disposer.dispose(usageView);
        }
        return myUsageViewRef.get();
      }
      return null;
    }

    private void openView(@NotNull final UsageViewImpl usageView) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          addContent(usageView, myPresentation);
          if (myListener != null) {
            myListener.usageViewCreated(usageView);
          }
          showToolWindow(false);
        }
      });
    }

    @Override
    public void run() {
      AtomicBoolean findUsagesStartedShown = new AtomicBoolean();
      searchUsages(findUsagesStartedShown);
      endSearchForUsages(findUsagesStartedShown);
    }

    private void searchUsages(@NotNull final AtomicBoolean findStartedBalloonShown) {
      ProgressIndicator indicator = ProgressWrapper.unwrap(ProgressManager.getInstance().getProgressIndicator());
      TooManyUsagesStatus.createFor(indicator);
      Alarm findUsagesStartedBalloon = new Alarm();
      findUsagesStartedBalloon.addRequest(new Runnable() {
        @Override
        public void run() {
          String balloon = "Searching for " + myPresentation.getUsagesString()+"...";
          notifyByFindBalloon(null, MessageType.WARNING, myProcessPresentation, UsageViewManagerImpl.this.myProject,
                              balloon);
          findStartedBalloonShown.set(true);
        }
      }, 300, ModalityState.NON_MODAL);
      UsageSearcher usageSearcher = mySearcherFactory.create();

      usageSearcher.generate(new Processor<Usage>() {
        @Override
        public boolean process(final Usage usage) {
          ProgressIndicator indicator = ProgressWrapper.unwrap(ProgressManager.getInstance().getProgressIndicator());
          if (indicator != null && indicator.isCanceled()) return false;
          TooManyUsagesStatus tooManyUsagesStatus = TooManyUsagesStatus.getFrom(indicator);
          boolean incrementCounter = !isSelfUsage(usage, mySearchFor);

          if (incrementCounter) {
            final int usageCount = myUsageCountWithoutDefinition.incrementAndGet();
            if (usageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
              myFirstUsage.compareAndSet(null, usage);
            }

            final UsageViewImpl usageView = getUsageView(indicator);

            if (usageCount > UsageLimitUtil.USAGES_LIMIT) {
              if (tooManyUsagesStatus.switchTooManyUsagesStatus()) {
                showTooManyUsagesWarning(myProject, tooManyUsagesStatus, indicator, myUsageCountWithoutDefinition.get(), usageView);
              }
            }

            if (usageView != null) {
              ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                  usageView.appendUsage(usage);
                }
              });
            }
          }
          return indicator == null || !indicator.isCanceled();
        }
      });
      if (getUsageView(indicator) != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            showToolWindow(true);
          }
        }, myProject.getDisposed());
      }
      Disposer.dispose(findUsagesStartedBalloon);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (findStartedBalloonShown.get()) {
            Balloon balloon = ToolWindowManager.getInstance(myProject).getToolWindowBalloon(ToolWindowId.FIND);
            if (balloon != null) {
              balloon.hide();
            }
          }
        }
      }, myProject.getDisposed());
    }

    private void endSearchForUsages(@NotNull final AtomicBoolean findStartedBalloonShown) {
      assert !ApplicationManager.getApplication().isDispatchThread() : Thread.currentThread();
      int usageCount = myUsageCountWithoutDefinition.get();
      if (usageCount == 0 && myProcessPresentation.isShowNotFoundMessage()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              final List<Action> notFoundActions = myProcessPresentation.getNotFoundActions();
              final String message = UsageViewBundle.message("dialog.no.usages.found.in",
                                                             StringUtil.decapitalize(myPresentation.getUsagesString()),
                                                             myPresentation.getScopeText());

              if (notFoundActions.isEmpty()) {
                String[] lines = myProcessPresentation.isShowFindOptionsPrompt() ? new String[] {StringUtil.escapeXml(message), createOptionsHtml()} : new String[]{StringUtil.escapeXml(message)};
                notifyByFindBalloon(createGotToOptionsListener(mySearchFor),
                                    MessageType.INFO, myProcessPresentation, UsageViewManagerImpl.this.myProject, lines);
                findStartedBalloonShown.set(false);
              }
              else {
                List<String> titles = new ArrayList<String>(notFoundActions.size() + 1);
                titles.add(UsageViewBundle.message("dialog.button.ok"));
                for (Action action : notFoundActions) {
                  Object value = action.getValue(FindUsagesProcessPresentation.NAME_WITH_MNEMONIC_KEY);
                  if (value == null) value = action.getValue(Action.NAME);

                  titles.add((String)value);
                }

                int option = Messages.showDialog(myProject, message, UsageViewBundle.message("dialog.title.information"),
                                                 ArrayUtil.toStringArray(titles), 0, Messages.getInformationIcon());

                if (option > 0) {
                  notFoundActions.get(option - 1).actionPerformed(new ActionEvent(this, 0, titles.get(option)));
                }
              }
            }
          }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
      else if (usageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            Usage usage = myFirstUsage.get();
            if (usage.canNavigate()) {
              usage.navigate(true);
              flashUsageScriptaculously(usage);
            }
            notifyByFindBalloon(createGotToOptionsListener(mySearchFor),
                                MessageType.INFO, myProcessPresentation, UsageViewManagerImpl.this.myProject,"Only one usage found.", createOptionsHtml());
          }
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
      else {
        final UsageViewImpl usageView = myUsageViewRef.get();
        if (usageView != null) {
          usageView.drainQueuedUsageNodes();
          usageView.setSearchInProgress(false);
        }
        if (!myProcessPresentation.getLargeFiles().isEmpty()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              notifyByFindBalloon(null, MessageType.INFO, myProcessPresentation, UsageViewManagerImpl.this.myProject);
            }
          }, ModalityState.NON_MODAL, myProject.getDisposed());
        }
      }

      if (myListener != null) {
        myListener.findingUsagesFinished(myUsageViewRef.get());
      }
    }
  }

  private static void notifyByFindBalloon(final HyperlinkListener listener,
                                          @NotNull final MessageType info,
                                          @NotNull FindUsagesProcessPresentation processPresentation,
                                          @NotNull final Project project,
                                          @NotNull String... sLines) {
    com.intellij.usageView.UsageViewManager.getInstance(project); // in case tool window not registered

    final List<String> lines = new ArrayList<String>(Arrays.asList(sLines));
    final Collection<PsiFile> largeFiles = processPresentation.getLargeFiles();
    List<String> resultLines = new ArrayList<String>(lines);
    HyperlinkListener resultListener = listener;
    if (!largeFiles.isEmpty()) {
      String shortMessage = "(<a href='" + LARGE_FILES_HREF_TARGET + "'>"
                            + UsageViewBundle.message("large.files.were.ignored", largeFiles.size()) + "</a>)";

      resultLines.add(shortMessage);
      resultListener = new HyperlinkAdapter(){
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          if (e.getDescription().equals(LARGE_FILES_HREF_TARGET)) {
            String detailedMessage = detailedLargeFilesMessage(largeFiles);
            List<String> strings = new ArrayList<String>(lines);
            strings.add(detailedMessage);
            ToolWindowManager.getInstance(project).notifyByBalloon(ToolWindowId.FIND, info, wrapInHtml(strings), AllIcons.Actions.Find, listener);
          }
          else if (listener != null) {
            listener.hyperlinkUpdate(e);
          }
        }
      };
    }

    ToolWindowManager.getInstance(project).notifyByBalloon(ToolWindowId.FIND, info, wrapInHtml(resultLines), AllIcons.Actions.Find, resultListener);
  }

  @NotNull
  private static String wrapInHtml(@NotNull List<String> strings) {
    return XmlStringUtil.wrapInHtml(StringUtil.join(strings, "<br>"));
  }

  @NotNull
  private static String detailedLargeFilesMessage(@NotNull Collection<PsiFile> largeFiles) {
    String message = "";
    if (largeFiles.size() == 1) {
      final VirtualFile vFile = largeFiles.iterator().next().getVirtualFile();
      message += "File " + presentableFileInfo(vFile) + " is ";
    }
    else {
      message += "Files<br> ";

      int counter = 0;
      for (PsiFile file : largeFiles) {
        final VirtualFile vFile = file.getVirtualFile();
        message += presentableFileInfo(vFile) + "<br> ";
        if (counter++ > 10) break;
      }

      message += "are ";
    }

    message += "too large and cannot be scanned";
    return message;
  }

  @NotNull
  private static String presentableFileInfo(@NotNull VirtualFile vFile) {
    return getPresentablePath(vFile)
           + "&nbsp;("
           + presentableSize(getFileLength(vFile))
           + ")";
  }

  @NotNull
  public static String presentableSize(long bytes) {
    long megabytes = bytes / (1024 * 1024);
    return UsageViewBundle.message("find.file.size.megabytes", Long.toString(megabytes));
  }

  public static long getFileLength(@NotNull final VirtualFile virtualFile) {
    final long[] length = {-1L};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (!virtualFile.isValid()) return;
        if (virtualFile.getFileType().isBinary()) return;
        length[0] = virtualFile.getLength();
      }
    });
    return length[0];
  }

  @NotNull
  private static String getPresentablePath(@NotNull final VirtualFile virtualFile) {
    return "'" + ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return virtualFile.getPresentableUrl();
      }
    }) + "'";
  }

  @NotNull
  private HyperlinkListener createGotToOptionsListener(@NotNull final UsageTarget[] targets) {
    return new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if (e.getDescription().equals(FIND_OPTIONS_HREF_TARGET)) {
          FindManager.getInstance(myProject).showSettingsAndFindUsages(targets);
        }
      }
    };
  }

  @NotNull
  private static String createOptionsHtml() {
    String shortcutText = "";
    KeyboardShortcut shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut();
    if (shortcut != null) {
      shortcutText = "&nbsp;(" + KeymapUtil.getShortcutText(shortcut) + ")";
    }
    return "<a href='" + FIND_OPTIONS_HREF_TARGET + "'>Find Options...</a>" + shortcutText;
  }

  private static void flashUsageScriptaculously(@NotNull final Usage usage) {
    if (!(usage instanceof UsageInfo2UsageAdapter)) {
      return;
    }
    UsageInfo2UsageAdapter usageInfo = (UsageInfo2UsageAdapter)usage;

    Editor editor = usageInfo.openTextEditor(true);
    if (editor == null) return;
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);

    RangeBlinker rangeBlinker = new RangeBlinker(editor, attributes, 6);
    List<Segment> segments = new ArrayList<Segment>();
    CommonProcessors.CollectProcessor<Segment> processor = new CommonProcessors.CollectProcessor<Segment>(segments);
    usageInfo.processRangeMarkers(processor);
    rangeBlinker.resetMarkers(segments);
    rangeBlinker.startBlinking();
  }
}
