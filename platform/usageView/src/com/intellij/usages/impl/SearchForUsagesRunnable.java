/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.find.FindManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.RangeBlinker;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class SearchForUsagesRunnable implements Runnable {
  @NonNls private static final String FIND_OPTIONS_HREF_TARGET = "FindOptions";
  @NonNls private static final String SEARCH_IN_PROJECT_HREF_TARGET = "SearchInProject";
  @NonNls private static final String LARGE_FILES_HREF_TARGET = "LargeFiles";
  @NonNls private static final String SHOW_PROJECT_FILE_OCCURRENCES_HREF_TARGET = "SHOW_PROJECT_FILE_OCCURRENCES";
  private final AtomicInteger myUsageCountWithoutDefinition = new AtomicInteger(0);
  private final AtomicReference<Usage> myFirstUsage = new AtomicReference<>();
  @NotNull
  private final Project myProject;
  private final AtomicReference<UsageViewImpl> myUsageViewRef;
  private final UsageViewPresentation myPresentation;
  private final UsageTarget[] mySearchFor;
  private final Factory<UsageSearcher> mySearcherFactory;
  private final FindUsagesProcessPresentation myProcessPresentation;
  @NotNull private final SearchScope mySearchScopeToWarnOfFallingOutOf;
  private final UsageViewManager.UsageViewStateListener myListener;
  private final UsageViewManagerImpl myUsageViewManager;
  private final AtomicInteger myOutOfScopeUsages = new AtomicInteger();

  SearchForUsagesRunnable(@NotNull UsageViewManagerImpl usageViewManager,
                          @NotNull Project project,
                          @NotNull AtomicReference<UsageViewImpl> usageViewRef,
                          @NotNull UsageViewPresentation presentation,
                          @NotNull UsageTarget[] searchFor,
                          @NotNull Factory<UsageSearcher> searcherFactory,
                          @NotNull FindUsagesProcessPresentation processPresentation,
                          @NotNull SearchScope searchScopeToWarnOfFallingOutOf,
                          @Nullable UsageViewManager.UsageViewStateListener listener) {
    myProject = project;
    myUsageViewRef = usageViewRef;
    myPresentation = presentation;
    mySearchFor = searchFor;
    mySearcherFactory = searcherFactory;
    myProcessPresentation = processPresentation;
    mySearchScopeToWarnOfFallingOutOf = searchScopeToWarnOfFallingOutOf;
    myListener = listener;
    myUsageViewManager = usageViewManager;
  }

  @NotNull
  private static String createOptionsHtml(@NonNls UsageTarget[] searchFor) {
    KeyboardShortcut shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut(searchFor);
    String shortcutText = "";
    if (shortcut != null) {
      shortcutText = "&nbsp;(" + KeymapUtil.getShortcutText(shortcut) + ")";
    }
    return "<a href='" + FIND_OPTIONS_HREF_TARGET + "'>Find Options...</a>" + shortcutText;
  }

  @NotNull
  private static String createSearchInProjectHtml() {
    return "<a href='" + SEARCH_IN_PROJECT_HREF_TARGET + "'>Search in Project</a>";
  }

  private void notifyByFindBalloon(@Nullable final HyperlinkListener listener,
                                   @NotNull final MessageType messageType,
                                   @NotNull final List<String> lines) {
    com.intellij.usageView.UsageViewManager.getInstance(myProject); // in case tool window not registered

    final Collection<VirtualFile> largeFiles = myProcessPresentation.getLargeFiles();
    List<String> resultLines = new ArrayList<>(lines);
    HyperlinkListener resultListener = listener;
    if (!largeFiles.isEmpty()) {
      String shortMessage = "(<a href='" + LARGE_FILES_HREF_TARGET + "'>"
                            + UsageViewBundle.message("large.files.were.ignored", largeFiles.size()) + "</a>)";

      resultLines.add(shortMessage);
      resultListener = addHrefHandling(resultListener, LARGE_FILES_HREF_TARGET, () -> {
        String detailedMessage = detailedLargeFilesMessage(largeFiles);
        List<String> strings = new ArrayList<>(lines);
        strings.add(detailedMessage);
        //noinspection SSBasedInspection
        ToolWindowManager.getInstance(myProject).notifyByBalloon(ToolWindowId.FIND, messageType, wrapInHtml(strings), AllIcons.Actions.Find, listener);
      });
    }

    Runnable searchIncludingProjectFileUsages = myProcessPresentation.searchIncludingProjectFileUsages();
    if (searchIncludingProjectFileUsages != null) {
      resultLines.add("Occurrences in project configuration files are skipped. " +
                      "<a href='" + SHOW_PROJECT_FILE_OCCURRENCES_HREF_TARGET + "'>Include them</a>");
      resultListener = addHrefHandling(resultListener, SHOW_PROJECT_FILE_OCCURRENCES_HREF_TARGET, searchIncludingProjectFileUsages);
    }

    Collection<UnloadedModuleDescription> unloaded = getUnloadedModulesBelongingToScope();
    MessageType actualType = messageType;
    if (!unloaded.isEmpty()) {
      if (actualType == MessageType.INFO) {
        actualType = MessageType.WARNING;
      }
      resultLines.add(mayHaveUsagesInUnloadedModulesMessage(unloaded));
    }

    //noinspection SSBasedInspection
    ToolWindowManager.getInstance(myProject).notifyByBalloon(ToolWindowId.FIND, actualType, wrapInHtml(resultLines), AllIcons.Actions.Find, resultListener);
  }

  private Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return ReadAction.compute(() -> {
      if (!(mySearchScopeToWarnOfFallingOutOf instanceof GlobalSearchScope)) return Collections.emptySet();
      Collection<UnloadedModuleDescription> unloadedInSearchScope =
        ((GlobalSearchScope)mySearchScopeToWarnOfFallingOutOf).getUnloadedModulesBelongingToScope();
      Set<UnloadedModuleDescription> unloadedInUseScope = getUnloadedModulesBelongingToUseScopes();
      if (unloadedInUseScope != null) {
        //when searching for usages of PsiElements return only those unloaded modules which may contain references to the elements, this way
        // we won't show a warning if e.g. 'find usages' for a private method is invoked
        return ContainerUtil.intersection(unloadedInSearchScope, unloadedInUseScope);
      }
      return unloadedInSearchScope;
    });
  }

  private Set<UnloadedModuleDescription> getUnloadedModulesBelongingToUseScopes() {
    Set<UnloadedModuleDescription> resolveScope = new LinkedHashSet<>();
    for (UsageTarget target : mySearchFor) {
      if (!(target instanceof PsiElementUsageTarget)) return null;
      PsiElement element = ((PsiElementUsageTarget)target).getElement();
      if (element == null) return null;
      SearchScope useScope = element.getUseScope();
      if (useScope instanceof GlobalSearchScope) {
        resolveScope.addAll(((GlobalSearchScope)useScope).getUnloadedModulesBelongingToScope());
      }
    }
    return resolveScope;
  }

  private static HyperlinkListener addHrefHandling(@Nullable final HyperlinkListener listener,
                                                   @NotNull final String hrefTarget, @NotNull final Runnable handler) {
    return new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if (e.getDescription().equals(hrefTarget)) {
          handler.run();
        }
        else if (listener != null) {
          listener.hyperlinkUpdate(e);
        }
      }
    };
  }

  @NotNull
  private static String wrapInHtml(@NotNull List<String> strings) {
    return XmlStringUtil.wrapInHtml(StringUtil.join(strings, "<br>"));
  }

  @NotNull
  private static String detailedLargeFilesMessage(@NotNull Collection<VirtualFile> largeFiles) {
    String message = "";
    if (largeFiles.size() == 1) {
      final VirtualFile vFile = largeFiles.iterator().next();
      message += "File " + presentableFileInfo(vFile) + " is ";
    }
    else {
      message += "Files<br> ";

      int counter = 0;
      for (VirtualFile vFile : largeFiles) {
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
           + UsageViewManagerImpl.presentableSize(UsageViewManagerImpl.getFileLength(vFile))
           + ")";
  }

  @NotNull
  private static String getPresentablePath(@NotNull final VirtualFile virtualFile) {
    return "'" + ReadAction.compute(virtualFile::getPresentableUrl) + "'";
  }

  @NotNull
  private HyperlinkListener createGotToOptionsListener(@NotNull final UsageTarget[] targets) {
    return new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if (e.getDescription().equals(FIND_OPTIONS_HREF_TARGET)) {
          TransactionGuard.getInstance().submitTransactionAndWait(
            () -> FindManager.getInstance(myProject).showSettingsAndFindUsages(targets));
        }
      }
    };
  }
  @NotNull
  private HyperlinkListener createSearchInProjectListener() {
    return new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if (e.getDescription().equals(SEARCH_IN_PROJECT_HREF_TARGET)) {
          PsiElement psiElement = getPsiElement(mySearchFor);
          if (psiElement != null) {
            TransactionGuard.getInstance().submitTransactionAndWait(
              () -> FindManager.getInstance(myProject).findUsagesInScope(psiElement, GlobalSearchScope.projectScope(myProject)));
          }
        }
      }
    };
  }

  private static PsiElement getPsiElement(@NotNull UsageTarget[] searchFor) {
    final UsageTarget target = searchFor[0];
    if (!(target instanceof PsiElementUsageTarget)) return null;
    return ReadAction.compute(((PsiElementUsageTarget)target)::getElement);
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
    List<Segment> segments = new ArrayList<>();
    Processor<Segment> processor = Processors.cancelableCollectProcessor(segments);
    usageInfo.processRangeMarkers(processor);
    rangeBlinker.resetMarkers(segments);
    rangeBlinker.startBlinking();
  }

  private UsageViewImpl getUsageView(@NotNull ProgressIndicator indicator) {
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
          ApplicationManager.getApplication().runReadAction(() -> finalUsageView.appendUsage(firstUsage));
        }
      }
      else {
        UsageViewImpl finalUsageView = usageView;
        // later because dispose does some sort of swing magic e.g. AnAction.unregisterCustomShortcutSet()
        UIUtil.invokeLaterIfNeeded(() -> Disposer.dispose(finalUsageView));
      }
      return myUsageViewRef.get();
    }
    return null;
  }

  private void openView(@NotNull final UsageViewImpl usageView) {
    SwingUtilities.invokeLater(() -> {
      if (myProject.isDisposed()) return;
      myUsageViewManager.addContent(usageView, myPresentation);
      if (myListener != null) {
        myListener.usageViewCreated(usageView);
      }
      myUsageViewManager.showToolWindow(false);
    });
  }

  @Override
  public void run() {
    PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();

    AtomicBoolean findUsagesStartedShown = new AtomicBoolean();
    searchUsages(findUsagesStartedShown);
    endSearchForUsages(findUsagesStartedShown);

    snapshot.logResponsivenessSinceCreation("Find Usages");
  }

  private void searchUsages(@NotNull final AtomicBoolean findStartedBalloonShown) {
    ProgressIndicator indicator = ProgressWrapper.unwrap(ProgressManager.getInstance().getProgressIndicator());
    if (indicator == null) throw new IllegalStateException("must run find usages under progress");
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      CoreProgressManager.assertUnderProgress(indicator);
    }
    TooManyUsagesStatus.createFor(indicator);
    Alarm findUsagesStartedBalloon = new Alarm();
    findUsagesStartedBalloon.addRequest(() -> {
      notifyByFindBalloon(null, MessageType.WARNING,
                          Collections.singletonList(StringUtil.escapeXml(UsageViewManagerImpl.getProgressTitle(myPresentation))));
      findStartedBalloonShown.set(true);
    }, 300, ModalityState.NON_MODAL);
    UsageSearcher usageSearcher = mySearcherFactory.create();

    usageSearcher.generate(usage -> {
      ProgressIndicator indicator1 = ProgressWrapper.unwrap(ProgressManager.getInstance().getProgressIndicator());
      if (indicator1 == null) throw new IllegalStateException("must run find usages under progress");
      if (indicator1.isCanceled()) return false;

      if (!UsageViewManagerImpl.isInScope(usage, mySearchScopeToWarnOfFallingOutOf)) {
        myOutOfScopeUsages.incrementAndGet();
        return true;
      }

      boolean incrementCounter = !UsageViewManager.isSelfUsage(usage, mySearchFor);

      if (incrementCounter) {
        final int usageCount = myUsageCountWithoutDefinition.incrementAndGet();
        if (usageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
          myFirstUsage.compareAndSet(null, usage);
        }

        final UsageViewImpl usageView = getUsageView(indicator1);

        TooManyUsagesStatus tooManyUsagesStatus= TooManyUsagesStatus.getFrom(indicator1);
        if (usageCount > UsageLimitUtil.USAGES_LIMIT && tooManyUsagesStatus.switchTooManyUsagesStatus()) {
          UsageViewManagerImpl.showTooManyUsagesWarningLater(myProject, tooManyUsagesStatus, indicator1, myPresentation, usageCount, usageView);
        }
        tooManyUsagesStatus.pauseProcessingIfTooManyUsages();
        if (usageView != null) {
          ApplicationManager.getApplication().runReadAction(() -> usageView.appendUsage(usage));
        }
      }
      return !indicator1.isCanceled();
    });
    if (getUsageView(indicator) != null) {
      ApplicationManager.getApplication().invokeLater(() -> myUsageViewManager.showToolWindow(true), myProject.getDisposed());
    }
    Disposer.dispose(findUsagesStartedBalloon);
    ApplicationManager.getApplication().invokeLater(() -> {
      if (findStartedBalloonShown.get()) {
        Balloon balloon = ToolWindowManager.getInstance(myProject).getToolWindowBalloon(ToolWindowId.FIND);
        if (balloon != null) {
          balloon.hide();
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
            if (myProcessPresentation.isCanceled()) {
              notifyByFindBalloon(null, MessageType.WARNING, Collections.singletonList("Usage search was canceled"));
              findStartedBalloonShown.set(false);
              return;
            }

            final String message = UsageViewBundle.message("dialog.no.usages.found.in",
                                                           StringUtil.decapitalize(myPresentation.getUsagesString()),
                                                           myPresentation.getScopeText(),
                                                           myPresentation.getContextText()
                                                           );

            List<String> lines = new ArrayList<>();
            lines.add(StringUtil.escapeXml(message));
            if (myOutOfScopeUsages.get() != 0) {
              lines.add(UsageViewManagerImpl.outOfScopeMessage(myOutOfScopeUsages.get(), mySearchScopeToWarnOfFallingOutOf));
            }
            if (myProcessPresentation.isShowFindOptionsPrompt()) {
              lines.add(createOptionsHtml(mySearchFor));
            }
            MessageType type = myOutOfScopeUsages.get() == 0 ? MessageType.INFO : MessageType.WARNING;
            notifyByFindBalloon(createGotToOptionsListener(mySearchFor), type, lines);
            findStartedBalloonShown.set(false);
          }
        }, ModalityState.NON_MODAL, myProject.getDisposed());
    }
    else if (usageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        Usage usage = myFirstUsage.get();
        if (usage.canNavigate()) {
          usage.navigate(true);
          flashUsageScriptaculously(usage);
        }
        List<String> lines = new ArrayList<>();

        lines.add("Only one usage found.");
        if (myOutOfScopeUsages.get() != 0) {
          lines.add(UsageViewManagerImpl.outOfScopeMessage(myOutOfScopeUsages.get(), mySearchScopeToWarnOfFallingOutOf));
        }
        lines.add(createOptionsHtml(mySearchFor));
        MessageType type = myOutOfScopeUsages.get() == 0 ? MessageType.INFO : MessageType.WARNING;
        notifyByFindBalloon(createGotToOptionsListener(mySearchFor), type, lines);
      }, ModalityState.NON_MODAL, myProject.getDisposed());
    }
    else {
      final UsageViewImpl usageView = myUsageViewRef.get();
      if (usageView != null) {
        usageView.drainQueuedUsageNodes();
        usageView.setSearchInProgress(false);
      }

      final List<String> lines;
      final HyperlinkListener hyperlinkListener;
      if (myOutOfScopeUsages.get() == 0 || getPsiElement(mySearchFor)==null) {
        lines = Collections.emptyList();
        hyperlinkListener = null;
      }
      else {
        lines = Arrays.asList(UsageViewManagerImpl.outOfScopeMessage(myOutOfScopeUsages.get(), mySearchScopeToWarnOfFallingOutOf), createSearchInProjectHtml());
        hyperlinkListener = createSearchInProjectListener();
      }

      if (!myProcessPresentation.getLargeFiles().isEmpty() ||
          myOutOfScopeUsages.get() != 0 ||
          myProcessPresentation.searchIncludingProjectFileUsages() != null ||
          !getUnloadedModulesBelongingToScope().isEmpty()) {
        ApplicationManager.getApplication().invokeLater(() -> {
          MessageType type = myOutOfScopeUsages.get() == 0 ? MessageType.INFO : MessageType.WARNING;
          notifyByFindBalloon(hyperlinkListener, type, lines);
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
    }

    if (myListener != null) {
      myListener.findingUsagesFinished(myUsageViewRef.get());
    }
  }

  @NotNull
  private static String mayHaveUsagesInUnloadedModulesMessage(@NotNull Collection<UnloadedModuleDescription> unloadedModules) {
    String modulesText = unloadedModules.size() > 1 ? unloadedModules.size() + " unloaded modules"
                                                    : "unloaded module '" + ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(unloadedModules)).getName() + "'";
    return "Occurrences in " + modulesText + " may be skipped. Load all modules and repeat the search to get complete results.";
  }
}
