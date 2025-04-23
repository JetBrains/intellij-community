// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.HighlightingPassesCache;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.XDebuggerActionsCollector;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList.ItemWithSeparatorAbove;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerEmbeddedComboBox;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class XFramesView extends XDebugView {
  private static final Logger LOG = Logger.getInstance(XFramesView.class);

  private final Project myProject;
  private final @NotNull WeakReference<XDebugSessionProxy> mySessionRef;
  private final JPanel myMainPanel;
  private final XDebuggerFramesList myFramesList;
  private final JScrollPane myScrollPane;
  private final ComboBox<XExecutionStack> myThreadComboBox;
  private final Map<XExecutionStack, XStackFrame> myExecutionStacksWithSelection = new HashMap<>();
  private final AutoScrollToSourceHandler myFrameSelectionHandler;
  private XExecutionStack mySelectedStack;
  private XStackFrame mySelectedFrame;
  private Rectangle myVisibleRect;
  private boolean myListenersEnabled;
  private final Map<XExecutionStack, StackFramesListBuilder> myBuilders = new HashMap<>();
  private final Wrapper myThreadsPanel;
  private boolean myThreadsCalculated;
  private boolean myRefresh;

  public XFramesView(@NotNull XDebugSession session) {
    this(XDebugSessionProxyKeeperKt.asProxy(session));
  }

  public XFramesView(@NotNull XDebugSessionProxy sessionProxy) {
    myProject = sessionProxy.getProject();
    mySessionRef = new WeakReference<>(sessionProxy);
    myMainPanel = new JPanel(new BorderLayout());

    myFrameSelectionHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() { return myListenersEnabled; }

      @Override
      protected void setAutoScrollMode(boolean state) { }

      @Override
      protected boolean needToCheckFocus() { return false; }

      @RequiresEdt
      @Override
      protected void scrollToSource(@NotNull Component list) {
        if (myListenersEnabled) {
          processFrameSelection(getSession(), true, myRefresh);
        }
      }
    };
    myFramesList = new XDebuggerFramesList(myProject, sessionProxy) {
      @Override
      protected @NotNull OccurenceInfo goOccurrence(int step) {
        OccurenceInfo info = super.goOccurrence(step);
        ScrollingUtil.ensureIndexIsVisible(this, getSelectedIndex(), step);
        return info;
      }

      @Override
      protected @NotNull Navigatable getFrameNavigatable(@NotNull XStackFrame frame, boolean isMainSourceKindPreferred) {
        XSourcePosition position = getFrameSourcePosition(frame, isMainSourceKindPreferred);
        Navigatable navigatable = position != null ? position.createNavigatable(sessionProxy.getProject()) : null;
        return new NavigatableAdapter() {
          @Override
          public void navigate(boolean requestFocus) {
            if (navigatable != null && navigatable.canNavigate()) navigatable.navigate(requestFocus);
            handleFrameSelection();
          }
        };
      }

      private @Nullable XSourcePosition getFrameSourcePosition(@NotNull XStackFrame frame, boolean isMainSourceKindPreferred) {
        if (isMainSourceKindPreferred) {
          XSourcePosition position = frame.getSourcePosition();
          if (position != null) {
            return position;
          }
        }
        return sessionProxy.getFrameSourcePosition(frame);
      }
    };

    installSpeedSearch(myFramesList);

    myFrameSelectionHandler.install(myFramesList);
    EditSourceOnDoubleClickHandler.install(myFramesList);

    myFramesList.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        int i = myFramesList.locationToIndex(new Point(x, y));
        if (i != -1) myFramesList.selectFrame(i);
        ActionManager actionManager = ActionManager.getInstance();
        ActionGroup group = (ActionGroup)actionManager.getAction(XDebuggerActions.FRAMES_TREE_POPUP_GROUP);
        actionManager.createActionPopupMenu("XDebuggerFramesList", group).getComponent().show(comp, x, y);
      }
    });

    myScrollPane = ScrollPaneFactory.createScrollPane(myFramesList);
    Component centerComponent = DebuggerUIUtil.wrapWithAntiFlickeringPanel(myScrollPane);
    myMainPanel.add(centerComponent, BorderLayout.CENTER);

    myThreadComboBox = new XDebuggerEmbeddedComboBox<>();
    myThreadComboBox.setSwingPopup(false);
    myThreadComboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      if (value != null) {
        label.setText(value.getDisplayName());
        label.setIcon(value.getIcon());
      }
      else if (index >= 0) {
        label.setText(CommonBundle.getLoadingTreeNodeText());
      }
    }));
    myThreadComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        if (!myListenersEnabled) {
          return;
        }

        if (e.getStateChange() == ItemEvent.SELECTED) {
          Object item = e.getItem();
          if (item != mySelectedStack && item instanceof XExecutionStack) {
            XDebugSessionProxy session = getSession();
            if (session != null) {
              myRefresh = false;
              updateFrames((XExecutionStack)item, session, null, false);
              XDebuggerActionsCollector.threadSelected.log(XDebuggerActionsCollector.PLACE_FRAMES_VIEW);
            }
          }
        }
      }
    });
    myThreadComboBox.addPopupMenuListener(new PopupMenuListenerAdapter() {
      ThreadsBuilder myBuilder;

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        stopBuilder();
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
        stopBuilder();
      }

      private void stopBuilder() {
        if (myBuilder != null) {
          myBuilder.setObsolete();
          myBuilder = null;
        }
      }

      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        XDebugSessionProxy session = getSession();
        // TODO there's, ostensibly, a long-living bug: thread list may change over time on a breakpoint with suspend thread policy;
        //  but myThreadsCalculated doesn't address this issue
        if (session == null || !session.hasSuspendContext() || myThreadsCalculated) {
          return;
        }
        myBuilder = new ThreadsBuilder();
        session.computeExecutionStacks(() -> myBuilder);
      }
    });

    ActionToolbarImpl toolbar = createToolbar();
    myThreadsPanel = new Wrapper();
    toolbar.setOpaque(false);
    ((XDebuggerEmbeddedComboBox<XExecutionStack>)myThreadComboBox).setExtension(toolbar);
    myMainPanel.add(myThreadsPanel, BorderLayout.NORTH);
    myMainPanel.setFocusCycleRoot(true);
    myMainPanel.setFocusTraversalPolicy(new MyFocusPolicy());
    addFramesNavigationAd(myMainPanel);
  }

  private static void installSpeedSearch(XDebuggerFramesList framesList) {
    //noinspection unchecked
    ListSpeedSearch.installOn(framesList, obj -> {
      //we have to use reflection because XDebuggerFramesList extends from JBList without generic parameter
      if (obj instanceof XStackFrame frame) {
        return getStackFramePresentableText(frame);
      }
      else {
        return null;
      }
    });
  }

  /**
   * Get the exact text which is used for the UI node representing the frame.
   * <p/>
   * NOTE 1: we can't rely on {@link XStackFrame#toString()} because it doesn't have any contract
   * <p/>
   * NOTE 2: this logic is called only when users type in the frames list.
   * So the performance of the default case (when speed search is not used) is not affected.
   * But when the user types in the speedsearch field the nodes are effectively rendered twice:
   * 1) for presentation in UI
   * 2) for speedsearch
   */
  private static @NotNull String getStackFramePresentableText(XStackFrame frame) {
    StringBuilderTextContainer builder = new StringBuilderTextContainer();
    frame.customizePresentation(builder);
    return builder.getText();
  }

  /**
   * Text container, which collects all text fragments and ignores all text attributes
   *
   * @implNote this class is not thread safe
   * @implNote this class could be extracted somewhere in `com.intellij.ui` package as it's quite simple.
   * Similar minimalistic implementations can be found in {@link SimpleColoredText} and {@link TextTransferable.ColoredStringBuilder}.
   * However, those are not as minimalistic as this one.
   */
  private static class StringBuilderTextContainer implements ColoredTextContainer {
    private final StringBuilder builder = new StringBuilder();

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
      builder.append(fragment);
    }

    public String getText() {
      return builder.toString();
    }
  }

  public void onFrameSelectionKeyPressed(@NotNull Consumer<? super XStackFrame> handler) {
    myFramesList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE || key == KeyEvent.VK_RIGHT) {
          handleFrameSelection();
          ApplicationManager.getApplication().invokeLater(() -> handler.accept(myFramesList.getSelectedFrame()));
        }
      }
    });
  }

  private void handleFrameSelection() {
    myFrameSelectionHandler.onMouseClicked(myFramesList);
  }

  private static @Nullable @NlsSafe String getShortcutText(@NotNull @NonNls String actionId) {
    KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut(actionId);
    if (shortcut == null) return null;
    return KeymapUtil.getShortcutText(shortcut);
  }

  private class MyFocusPolicy extends ComponentsListFocusTraversalPolicy {
    @Override
    protected @NotNull List<Component> getOrderedComponents() {
      return Arrays.asList(myFramesList,
                           myThreadComboBox);
    }
  }

  public XDebuggerFramesList getFramesList() {
    return myFramesList;
  }

  private class ThreadsBuilder implements XSuspendContext.XExecutionStackContainer {
    private volatile boolean myObsolete;
    private boolean myAddBeforeSelection = true;

    ThreadsBuilder() {
      myThreadComboBox.addItem(null); // rendered as "Loading..."
    }

    @Override
    public void addExecutionStack(@NotNull List<? extends XExecutionStack> executionStacks, boolean last) {
      List<? extends XExecutionStack> copyStacks = new ArrayList<>(executionStacks); // to capture the current List elements
      ApplicationManager.getApplication().invokeLater(() -> {
        int initialCount = myThreadComboBox.getItemCount();
        if (last) {
          removeLoading();
          myThreadsCalculated = true;
        }
        myAddBeforeSelection = addExecutionStacks(copyStacks, myAddBeforeSelection);

        // reopen if popups height changed
        int newCount = myThreadComboBox.getItemCount();
        int maxComboboxRows = myThreadComboBox.getMaximumRowCount();
        if (newCount != initialCount && (initialCount < maxComboboxRows || newCount < maxComboboxRows)) {
          ComboPopup popup = myThreadComboBox.getPopup();
          if (popup != null && popup.isVisible()) {
            popup.hide();
            popup.show();
          }
        }
      });
    }

    @Override
    public void errorOccurred(@NotNull String errorMessage) {
      ApplicationManager.getApplication().invokeLater(this::removeLoading);
    }

    @Override
    public boolean isObsolete() {
      return myObsolete;
    }

    public void setObsolete() {
      if (!myObsolete) {
        myObsolete = true;
        removeLoading();
      }
    }

    void removeLoading() {
      myThreadComboBox.removeItem(null);
    }
  }

  private ActionToolbarImpl createToolbar() {
    final DefaultActionGroup framesGroup = new DefaultActionGroup();

    framesGroup.addAll(ActionManager.getInstance().getAction(XDebuggerActions.FRAMES_TOP_TOOLBAR_GROUP));

    final ActionToolbarImpl toolbar =
      (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, framesGroup, true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.setTargetComponent(myFramesList);
    return toolbar;
  }

  private StackFramesListBuilder getOrCreateBuilder(XExecutionStack executionStack, XDebugSessionProxy sessionProxy) {
    return myBuilders.computeIfAbsent(executionStack, k -> new StackFramesListBuilder(executionStack, sessionProxy));
  }

  private void withCurrentBuilder(Consumer<? super StackFramesListBuilder> consumer) {
    StackFramesListBuilder builder = myBuilders.get(mySelectedStack);
    if (builder != null) {
      consumer.accept(builder);
    }
  }

  @Override
  public void processSessionEvent(@NotNull SessionEvent event, @NotNull XDebugSessionProxy session) {
    myRefresh = event == SessionEvent.SETTINGS_CHANGED;

    if (event == SessionEvent.BEFORE_RESUME) {
      if (DebuggerUIUtil.freezePaintingToReduceFlickering(myScrollPane.getParent())) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          myScrollPane.getHorizontalScrollBar().setValue(0);
          myScrollPane.getVerticalScrollBar().setValue(0);
        });
      }
      return;
    }

    XExecutionStack currentExecutionStack = session.getCurrentExecutionStack();
    XStackFrame currentStackFrame = session.getCurrentStackFrame();
    boolean hasSuspendContext = session.hasSuspendContext();

    if (event == SessionEvent.FRAME_CHANGED && Objects.equals(mySelectedStack, currentExecutionStack)) {
      ThreadingAssertions.assertEventDispatchThread();
      if (currentStackFrame != null) {
        myFramesList.setSelectedValue(currentStackFrame, true);
        mySelectedFrame = currentStackFrame;
        myExecutionStacksWithSelection.put(mySelectedStack, currentStackFrame);
      }
      return;
    }

    EdtExecutorService.getInstance().execute(() -> {
      if (event != SessionEvent.SETTINGS_CHANGED) {
        mySelectedFrame = null;
        mySelectedStack = null;
        myVisibleRect = null;
      }
      else {
        myVisibleRect = myFramesList.getVisibleRect();
      }

      boolean shouldRefresh = event == SessionEvent.SETTINGS_CHANGED;
      if (shouldRefresh && mySelectedStack != null) {
        StackFramesListBuilder previousBuilder = myBuilders.get(mySelectedStack);
        if (previousBuilder != null && previousBuilder.myRunning && !previousBuilder.myRefresh) {
          // The previous non-refresh builder didn't finish yet, in that case the new builder should not be in the refresh mode.
          shouldRefresh = false;
        }
      }

      myListenersEnabled = false;
      myBuilders.values().forEach(StackFramesListBuilder::dispose);
      myBuilders.clear();

      if (!hasSuspendContext) {
        requestClear();
        return;
      }

      if (event == SessionEvent.PAUSED) {
        // clear immediately
        cancelClear();
        clear();
      }

      XExecutionStack activeExecutionStack = mySelectedStack != null ? mySelectedStack : currentExecutionStack;
      addExecutionStacks(Collections.singletonList(activeExecutionStack), false);

      myThreadComboBox.setSelectedItem(activeExecutionStack);
      boolean invisible = activeExecutionStack == null || StringUtil.isEmpty(activeExecutionStack.getDisplayName());
      if (invisible != (myThreadComboBox.getParent() == null)) {
        if (invisible) {
          myThreadsPanel.remove(myThreadComboBox);
          myThreadsPanel.setBorder(null);
        }
        else {
          myThreadsPanel.add(myThreadComboBox, BorderLayout.CENTER);
          myThreadsPanel.setBorder(new CustomLineBorder(0, 0, 1, 0));
        }
        myThreadsPanel.revalidate();
      }
      updateFrames(activeExecutionStack,
                   session,
                   event == SessionEvent.FRAME_CHANGED ? currentStackFrame : null,
                   shouldRefresh);
    });
  }

  @Override
  protected void clear() {
    myThreadComboBox.removeAllItems();
    myFramesList.clear();
    myThreadsCalculated = false;
    myExecutionStacksWithSelection.clear();
  }

  private boolean addExecutionStacks(List<? extends XExecutionStack> executionStacks, boolean addBeforeSelection) {
    int count = myThreadComboBox.getItemCount();
    boolean loading = count > 0 && myThreadComboBox.getItemAt(count - 1) == null;
    Object selectedItem = myThreadComboBox.getSelectedItem();
    for (XExecutionStack executionStack : executionStacks) {
      if (addBeforeSelection && executionStack.equals(selectedItem)) {
        addBeforeSelection = false;
      }
      if (!myExecutionStacksWithSelection.containsKey(executionStack)) {
        if (addBeforeSelection) {
          myThreadComboBox.insertItemAt(executionStack, myThreadComboBox.getSelectedIndex()); // add right before the selected node
        }
        else {
          if (loading) {
            myThreadComboBox.insertItemAt(executionStack, myThreadComboBox.getItemCount() - 1); // add right before the loading node
          }
          else {
            myThreadComboBox.addItem(executionStack);
          }
        }
        myExecutionStacksWithSelection.put(executionStack, null);
      }
    }
    return addBeforeSelection;
  }

  private void updateFrames(XExecutionStack executionStack,
                            @NotNull XDebugSessionProxy sessionProxy,
                            @Nullable XStackFrame frameToSelect,
                            boolean refresh) {
    if (mySelectedStack != null) {
      withCurrentBuilder(StackFramesListBuilder::stop);
    }

    mySelectedStack = executionStack;
    if (executionStack != null) {
      mySelectedFrame = myExecutionStacksWithSelection.get(executionStack);
      StackFramesListBuilder builder = getOrCreateBuilder(executionStack, sessionProxy);
      builder.setRefresh(refresh);
      builder.setToSelect(frameToSelect != null ? frameToSelect : mySelectedFrame);
      myListenersEnabled = false;
      boolean selected = builder.initModel(myFramesList.getModel());
      myListenersEnabled = !builder.start() || selected;
    }

    XDebugSessionProxy debugSession = getSession();
    if (debugSession != null && debugSession.isSteppingSuspendContext()) {
      myListenersEnabled = true;
    }
  }

  @Override
  public void dispose() {
  }

  private @Nullable XDebugSessionProxy getSession() {
    return mySessionRef.get();
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  @Override
  public JComponent getMainComponent() {
    return getMainPanel();
  }

  private void processFrameSelection(XDebugSessionProxy sessionProxy, boolean force, boolean refresh) {
    mySelectedFrame = myFramesList.getSelectedFrame();
    myExecutionStacksWithSelection.put(mySelectedStack, mySelectedFrame);
    withCurrentBuilder(b -> b.setToSelect(null));

    Object selected = myFramesList.getSelectedValue();
    if (selected instanceof XStackFrame) {
      if (sessionProxy != null) {
        if (force || (!refresh && sessionProxy.getCurrentStackFrame() != selected)) {
          int mySelectedFrameIndex = myFramesList.getSelectedIndex();
          sessionProxy.setCurrentStackFrame(mySelectedStack, (XStackFrame)selected, mySelectedFrameIndex == 0);
          if (force) {
            XDebuggerActionsCollector.frameSelected.log(XDebuggerActionsCollector.PLACE_FRAMES_VIEW);
          }
        }
      }
    }
  }

  private final class StackFramesListBuilder implements XStackFrameContainerEx {
    private XExecutionStack myExecutionStack;
    private final List<XStackFrame> myStackFrames;
    private String myErrorMessage;
    private int myNextFrameIndex;
    private volatile boolean myRunning;
    private long myStartTimeMs;
    private boolean myAllFramesLoaded;
    private final XDebugSessionProxy mySessionProxy;
    private Object myToSelect;
    private boolean myRefresh;

    private StackFramesListBuilder(final XExecutionStack executionStack, XDebugSessionProxy sessionProxy) {
      myExecutionStack = executionStack;
      mySessionProxy = sessionProxy;
      myStackFrames = new ArrayList<>();
    }

    void setToSelect(Object toSelect) {
      myToSelect = toSelect;
    }

    private void setRefresh(boolean refresh) {
      myRefresh = refresh;
    }

    @Override
    public void addStackFrames(final @NotNull List<? extends XStackFrame> stackFrames, final boolean last) {
      addStackFrames(stackFrames, null, last);
    }

    @Override
    public void addStackFrames(final @NotNull List<? extends XStackFrame> stackFrames, @Nullable XStackFrame toSelect, final boolean last) {
      if (isObsolete()) return;
      EdtExecutorService.getInstance().execute(() -> {
        if (isObsolete()) return;
        myStackFrames.addAll(stackFrames);
        addFrameListElements(stackFrames, last);

        if (toSelect != null && !myRefresh) {
          setToSelect(toSelect);
        }

        myNextFrameIndex += stackFrames.size();
        myAllFramesLoaded = last;

        selectCurrentFrame();

        if (last) {
          if (myVisibleRect != null) {
            myFramesList.scrollRectToVisible(myVisibleRect);
          }
          XDebuggerActionsCollector.logFramesUpdated(System.currentTimeMillis() - myStartTimeMs, myStackFrames);
          myRunning = false;
          myListenersEnabled = true;
        }
      });
    }

    @Override
    public void errorOccurred(final @NotNull String errorMessage) {
      if (isObsolete()) return;
      EdtExecutorService.getInstance().execute(() -> {
        if (isObsolete()) return;
        if (myErrorMessage == null) {
          myErrorMessage = errorMessage;
          addFrameListElements(Collections.singletonList(errorMessage), true);
          myRunning = false;
          myListenersEnabled = true;
        }
      });
    }

    private void addFrameListElements(final List<?> values, final boolean last) {
      if (myExecutionStack != null && myExecutionStack == mySelectedStack) {
        CollectionListModel model = myFramesList.getModel();
        int insertIndex = model.getSize();
        boolean loadingPresent = insertIndex > 0 && model.getElementAt(insertIndex - 1) == null;
        if (loadingPresent) {
          insertIndex--;
        }
        //noinspection unchecked
        model.addAll(insertIndex, values);

        scheduleFilesHighlighting(values, myProject);

        if (last) {
          if (loadingPresent) {
            model.remove(model.getSize() - 1);
          }
        }
        else if (!loadingPresent) {
          //noinspection unchecked
          model.add((Object)null);
        }
        myFramesList.repaint();
      }
    }

    private static void scheduleFilesHighlighting(@NotNull List<?> values, @NotNull Project project) {
      if (!Registry.is("highlighting.passes.cache")) return;

      List<VirtualFile> files = StreamEx.of(values).select(XStackFrame.class)
        .map(it -> ObjectUtils.doIfNotNull(it.getSourcePosition(), XSourcePosition::getFile))
        .filter(Objects::nonNull)
        .toList();

      HighlightingPassesCache.getInstance(project).schedule(files, true);
    }

    @Override
    public boolean isObsolete() {
      return !myRunning;
    }

    public void dispose() {
      myRunning = false;
      myStartTimeMs = 0;
      myExecutionStack = null;
    }

    public boolean start() {
      if (myExecutionStack == null || myErrorMessage != null) {
        return false;
      }
      myRunning = true;
      myStartTimeMs = System.currentTimeMillis();
      myExecutionStack.computeStackFrames(myNextFrameIndex, this);
      return true;
    }

    public void stop() {
      myRunning = false;
    }

    private boolean selectCurrentFrame() {
      if (selectFrame(myToSelect)) {
        myListenersEnabled = true;
        processFrameSelection(mySessionProxy, false, myRefresh);
        return true;
      }
      return false;
    }

    private boolean selectFrame(Object toSelect) {
      if (toSelect instanceof XStackFrame) {
        if (myFramesList.selectFrame((XStackFrame)toSelect)) return true;
        if (myAllFramesLoaded && myFramesList.getSelectedValue() == null) {
          LOG.warn("Frame was not found, it was either hidden without placeholder (" + HiddenStackFramesItem.class + ") or " + myToSelect.getClass() + " must correctly override equals");
        }
      }
      else if (toSelect instanceof Integer) {
        if (myFramesList.selectFrame((int)toSelect)) return true;
      }
      else if (toSelect == null && myFramesList.getSelectedIndex() == -1) {
        if (myFramesList.selectFrame(0)) return true;
      }
      return false;
    }

    @SuppressWarnings("unchecked")
    public boolean initModel(final CollectionListModel model) {
      model.replaceAll(myStackFrames);
      if (myErrorMessage != null) {
        model.add(myErrorMessage);
      }
      else if (!myAllFramesLoaded) {
        model.add((Object)null);
      }
      return selectCurrentFrame();
    }
  }

  @ApiStatus.Internal
  public @NotNull ComboBox<XExecutionStack> getThreadComboBox() {
    return myThreadComboBox;
  }

  /**
   * Synthetic frame which encapsulates hidden library frames as a single fold.
   *
   * @see #shouldFoldHiddenFrames()
   */
  public static class HiddenStackFramesItem extends XStackFrame implements XDebuggerFramesList.ItemWithCustomBackgroundColor,
                                                                           ItemWithSeparatorAbove,
                                                                           HiddenFramesStackFrame {
    final List<XStackFrame> hiddenFrames;

    public HiddenStackFramesItem(List<XStackFrame> hiddenFrames) {
      this.hiddenFrames = List.copyOf(hiddenFrames);
      if (hiddenFrames.isEmpty()) {
        throw new IllegalArgumentException();
      }
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
      component.append(XDebuggerBundle.message("label.folded.frames", hiddenFrames.size()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      component.setIcon(EmptyIcon.ICON_16);
    }

    @Override
    public @Nullable Color getBackgroundColor() {
      return null;
    }

    @Override
    public @NotNull List<XStackFrame> getHiddenFrames() {
      return hiddenFrames;
    }

    private Optional<ItemWithSeparatorAbove> findFrameWithSeparator() {
      // We check only the first frame; otherwise, it's not clear what to do.
      // Might be reconsidered in the future.
      return hiddenFrames.get(0) instanceof ItemWithSeparatorAbove frame
             ? Optional.of(frame)
             : Optional.empty();
    }

    @Override
    public boolean hasSeparatorAbove() {
      return findFrameWithSeparator().map(ItemWithSeparatorAbove::hasSeparatorAbove).orElse(false);
    }

    @Override
    public String getCaptionAboveOf() {
      return findFrameWithSeparator().map(ItemWithSeparatorAbove::getCaptionAboveOf).orElse(null);
    }
  }

  /**
   * Whether hidden frames are folded into a single-item placeholder.
   * Otherwise, they completely disappear.
   */
  public static boolean shouldFoldHiddenFrames() {
    return Registry.is("debugger.library.frames.fold.instead.of.hide");
  }


  static void addFramesNavigationAd(JPanel parent) {
    if (!(parent.getLayout() instanceof BorderLayout)) {
      return;
    }
    String prev = getShortcutText(IdeActions.ACTION_PREVIOUS_OCCURENCE);
    String next = getShortcutText(IdeActions.ACTION_NEXT_OCCURENCE);
    String propKey = "XFramesView.AdPanel.SwitchFrames.enabled";
    if (PropertiesComponent.getInstance().getBoolean(propKey, true) && prev != null && next != null) {
      String message = XDebuggerBundle.message("debugger.switch.frames.from.anywhere.hint", prev, next);
      var hint = new MyAdPanel(message, p -> {
        parent.remove(p);
        parent.revalidate();
        PropertiesComponent.getInstance().setValue(propKey, false, true);
      });
      parent.add(hint, BorderLayout.SOUTH);
    }
  }

  private static class MyAdPanel extends BorderLayoutPanel {

    MyAdPanel(@NotNull @NlsContexts.Label String message, @NotNull Consumer<? super MyAdPanel> closeListener) {
      var label = new JBLabel(message, UIUtil.ComponentStyle.SMALL);
      label.setForeground(UIUtil.getContextHelpForeground());
      label.setToolTipText(message);
      var closeButton = new JButton();
      closeButton.setText(null);
      closeButton.setOpaque(false);
      closeButton.setBorder(null);
      closeButton.setBorderPainted(false);
      closeButton.setContentAreaFilled(false);
      closeButton.setIcon(AllIcons.Actions.Close);
      closeButton.setRolloverIcon(AllIcons.Actions.CloseDarkGrey);
      closeButton.setToolTipText(CommonBundle.getCloseButtonText());
      closeButton.addActionListener(e -> {
        closeListener.accept(this);
      });
      var dim = new Dimension(AllIcons.Actions.Close.getIconHeight(), AllIcons.Actions.Close.getIconWidth());
      closeButton.setPreferredSize(dim);

      addToCenter(label);
      addToRight(closeButton);

      setBorder(JBUI.Borders.empty(3, 8, 3, 4));
    }
  }
}
