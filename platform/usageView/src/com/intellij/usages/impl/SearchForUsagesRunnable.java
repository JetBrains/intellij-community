// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.find.FindManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.*;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.RangeBlinker;
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

final class SearchForUsagesRunnable implements Runnable {
  @NonNls private static final String FIND_OPTIONS_HREF_TARGET = "FindOptions";
  @NonNls private static final String SEARCH_IN_PROJECT_HREF_TARGET = "SearchInProject";
  @NonNls private static final String LARGE_FILES_HREF_TARGET = "LargeFiles";
  @NonNls private static final String SHOW_PROJECT_FILE_OCCURRENCES_HREF_TARGET = "SHOW_PROJECT_FILE_OCCURRENCES";
  private final AtomicInteger myUsageCountWithoutDefinition = new AtomicInteger(0);
  private final AtomicReference<Usage> myFirstUsage = new AtomicReference<>();
  @NotNull
  private final Project myProject;
  private final AtomicReference<UsageViewEx> myUsageViewRef;
  private final UsageViewPresentation myPresentation;
  private final UsageTarget[] mySearchFor;
  private final Factory<? extends UsageSearcher> mySearcherFactory;
  private final FindUsagesProcessPresentation myProcessPresentation;
  @NotNull private final SearchScope mySearchScopeToWarnOfFallingOutOf;
  private final UsageViewManager.UsageViewStateListener myListener;
  private final UsageViewManagerImpl myUsageViewManager;
  private final AtomicInteger myOutOfScopeUsages = new AtomicInteger();

  SearchForUsagesRunnable(@NotNull UsageViewManagerImpl usageViewManager,
                          @NotNull Project project,
                          @NotNull AtomicReference<UsageViewEx> usageViewRef,
                          @NotNull UsageViewPresentation presentation,
                          UsageTarget @NotNull [] searchFor,
                          @NotNull Factory<? extends UsageSearcher> searcherFactory,
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
  private static String createOptionsHtml(@NonNls UsageTarget @NotNull [] searchFor) {
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
    UsageViewContentManager.getInstance(myProject); // in case tool window not registered

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

  @NotNull
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

  @NotNull
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
  private static String detailedLargeFilesMessage(@NotNull Collection<? extends VirtualFile> largeFiles) {
    String message;
    if (largeFiles.size() == 1) {
      final VirtualFile vFile = largeFiles.iterator().next();
      message = "File " + presentableFileInfo(vFile) + " is ";
    }
    else {
      message = "Files<br> "
      + StringUtil.join(ContainerUtil.getFirstItems(new ArrayList<>(largeFiles), 10), vFile -> presentableFileInfo(vFile), "<br> ")
      + "<br> are ";
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
  private HyperlinkListener createGotToOptionsListener(final UsageTarget @NotNull [] targets) {
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
  private HyperlinkListener createSearchInProjectListener() {
    return new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if (e.getDescription().equals(SEARCH_IN_PROJECT_HREF_TARGET)) {
          PsiElement psiElement = getPsiElement(mySearchFor);
          if (psiElement != null) {
            FindManager.getInstance(myProject).findUsagesInScope(psiElement, GlobalSearchScope.projectScope(myProject));
          }
        }
      }
    };
  }

  private static PsiElement getPsiElement(UsageTarget @NotNull [] searchFor) {
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

  private UsageViewEx getUsageView(@NotNull ProgressIndicator indicator, long startSearchStamp) {
    UsageViewEx usageView = myUsageViewRef.get();
    if (usageView != null) {
      return usageView;
    }

    int usageCount = myUsageCountWithoutDefinition.get();
    if (usageCount == 0 || usageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage() && System.currentTimeMillis() < startSearchStamp + 500) {
      return null;
    }

    usageView = myUsageViewManager.createUsageView(mySearchFor, Usage.EMPTY_ARRAY, myPresentation, mySearcherFactory);
    if (myUsageViewRef.compareAndSet(null, usageView)) {
      // associate progress only if created successfully, otherwise Dispose will cancel the actual progress, see IDEA-195542
      usageView.associateProgress(indicator);
      if (myProcessPresentation.isShowFindOptionsPrompt()) {
        openView(usageView);
      }
      else if (myListener != null) {
        SwingUtilities.invokeLater(() -> {
          if (!myProject.isDisposed()) {
            UsageViewEx uv = myUsageViewRef.get();
            if (uv != null) {
              myListener.usageViewCreated(uv);
            }
          }
        });
      }

      Usage firstUsage = myFirstUsage.get();
      if (firstUsage != null) {
        UsageViewEx finalUsageView = usageView;
        ApplicationManager.getApplication().runReadAction(() -> finalUsageView.appendUsage(firstUsage));
      }
    }
    else {
      UsageViewEx finalUsageView = usageView;
      Disposer.register(myProject, usageView);
      // UI thread because dispose does some sort of swing magic e.g. AnAction.unregisterCustomShortcutSet()
      AppUIExecutor.onUiThread(ModalityState.any()).expireWith(myProject).execute(() -> Disposer.dispose(finalUsageView));
    }
    return myUsageViewRef.get();
  }

  private void openView(@NotNull final UsageViewEx usageView) {
    SwingUtilities.invokeLater(() -> {
      if (myProject.isDisposed()) return;
      myUsageViewManager.showUsageView(usageView, myPresentation);
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
    ProgressIndicator current = ProgressManager.getInstance().getProgressIndicator();
    if (current == null) throw new IllegalStateException("must run find usages under progress");
    ProgressIndicator indicator = ProgressWrapper.unwrapAll(current);
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      CoreProgressManager.assertUnderProgress(indicator);
    }
    TooManyUsagesStatus.createFor(indicator);
    Alarm findUsagesStartedBalloon = new Alarm();
    findUsagesStartedBalloon.addRequest(() -> {
      notifyByFindBalloon(null, MessageType.WARNING,
                          Collections.singletonList(StringUtil.escapeXmlEntities(UsageViewManagerImpl.getProgressTitle(myPresentation))));
      findStartedBalloonShown.set(true);
    }, 300, ModalityState.NON_MODAL);
    UsageSearcher usageSearcher = mySearcherFactory.create();
    long startSearchStamp = System.currentTimeMillis();
    usageSearcher.generate(usage -> {
      ProgressIndicator currentIndicator = ProgressManager.getInstance().getProgressIndicator();
      if (currentIndicator == null) throw new IllegalStateException("must run find usages under progress");
      ProgressIndicator originalIndicator = ProgressWrapper.unwrapAll(current);
      ProgressManager.checkCanceled();

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

        UsageViewEx usageView = getUsageView(originalIndicator, startSearchStamp);

        TooManyUsagesStatus tooManyUsagesStatus= TooManyUsagesStatus.getFrom(originalIndicator);
        if (usageCount > UsageLimitUtil.USAGES_LIMIT && tooManyUsagesStatus.switchTooManyUsagesStatus()) {
          UsageViewManagerImpl.showTooManyUsagesWarningLater(myProject, tooManyUsagesStatus, originalIndicator, usageView);
        }
        tooManyUsagesStatus.pauseProcessingIfTooManyUsages();
        if (usageView != null) {
          ApplicationManager.getApplication().runReadAction(() -> usageView.appendUsage(usage));
        }
      }
      return true;
    });
    if (getUsageView(indicator, startSearchStamp) != null) {
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
    if (usageCount == 0) {
      if (myProcessPresentation.isShowNotFoundMessage()) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (myProcessPresentation.isCanceled()) {
            notifyByFindBalloon(null, MessageType.WARNING, Collections.singletonList("Usage search was canceled"));
            findStartedBalloonShown.set(false);
            return;
          }
          List<String> lines = new ArrayList<>();
          lines.add(StringUtil.escapeXmlEntities(myPresentation.getSearchString()));
          lines.add(UsageViewBundle.message("search.result.nothing.in.0", StringUtil.escapeXmlEntities(myPresentation.getScopeText())));
          if (myOutOfScopeUsages.get() != 0) {
            lines.add(UsageViewManagerImpl.outOfScopeMessage(myOutOfScopeUsages.get(), mySearchScopeToWarnOfFallingOutOf));
          }
          if (myProcessPresentation.isShowFindOptionsPrompt()) {
            lines.add(createOptionsHtml(mySearchFor));
          }
          MessageType type = myOutOfScopeUsages.get() == 0 ? MessageType.INFO : MessageType.WARNING;
          notifyByFindBalloon(createGotToOptionsListener(mySearchFor), type, lines);
          findStartedBalloonShown.set(false);
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
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
      final UsageViewEx usageView = myUsageViewRef.get();
      usageView.searchFinished();
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

    UsageViewEx usageView = myUsageViewRef.get();
    if (usageView != null) {
      usageView.waitForUpdateRequestsCompletion();
    }
    if (myListener != null) {
      myListener.findingUsagesFinished(usageView);
    }
  }

  @NotNull
  private static String mayHaveUsagesInUnloadedModulesMessage(@NotNull Collection<? extends UnloadedModuleDescription> unloadedModules) {
    String modulesText = unloadedModules.size() > 1 ? unloadedModules.size() + " unloaded modules"
                                                    : "unloaded module '" + Objects
                                                      .requireNonNull(ContainerUtil.getFirstItem(unloadedModules)).getName() + "'";
    return "Occurrences in " + modulesText + " may be skipped. Load all modules and repeat the search to get complete results.";
  }
}
