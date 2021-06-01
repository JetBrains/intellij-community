// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame;

import com.intellij.CommonBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.XDebuggerEmbeddedComboBox;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

public final class XFramesView extends XDebugView {
  private static final Logger LOG = Logger.getInstance(XFramesView.class);

  private final JPanel myMainPanel;
  private final XDebuggerFramesList myFramesList;
  private final ComboBox<XExecutionStack> myThreadComboBox;
  private final Object2IntMap<XExecutionStack> myExecutionStacksWithSelection = new Object2IntOpenHashMap<>();
  private final AutoScrollToSourceHandler myFrameSelectionHandler;
  private XExecutionStack mySelectedStack;
  private int mySelectedFrameIndex;
  private Rectangle myVisibleRect;
  private boolean myListenersEnabled;
  private final Map<XExecutionStack, StackFramesListBuilder> myBuilders = new HashMap<>();
  private final ActionToolbarImpl myToolbar;
  private final Wrapper myThreadsPanel;
  private boolean myThreadsCalculated;
  private boolean myRefresh;

  public XFramesView(@NotNull Project project) {
    myMainPanel = new JPanel(new BorderLayout());

    myFrameSelectionHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() { return true; }

      @Override
      protected void setAutoScrollMode(boolean state) { }

      @Override
      protected boolean needToCheckFocus() { return false; }

      @Override
      protected void scrollToSource(@NotNull Component list) {
        if (myListenersEnabled) {
          processFrameSelection(getSession(list), true);
        }
      }
    };
    myFramesList = new XDebuggerFramesList(project) {
      @Override
      protected @NotNull OccurenceInfo goOccurrence(int step) {
        OccurenceInfo info = super.goOccurrence(step);
        ScrollingUtil.ensureIndexIsVisible(this, getSelectedIndex(), step);
        return info;
      }

      @Override
      protected @NotNull Navigatable getSelectedFrameNavigatable() {
        Navigatable navigatable = super.getSelectedFrameNavigatable();
        return new NavigatableAdapter() {
          @Override
          public void navigate(boolean requestFocus) {
            if (navigatable != null && navigatable.canNavigate()) navigatable.navigate(requestFocus);
            handleFrameSelection();
          }
        };
      }
    };
    myFrameSelectionHandler.install(myFramesList);
    EditSourceOnDoubleClickHandler.install(myFramesList);

    myFramesList.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        int i = myFramesList.locationToIndex(new Point(x, y));
        if (i != -1) myFramesList.selectFrame(i);
        ActionManager actionManager = ActionManager.getInstance();
        ActionGroup group = (ActionGroup)actionManager.getAction(XDebuggerActions.FRAMES_TREE_POPUP_GROUP);
        actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group).getComponent().show(comp, x, y);
      }
    });

    myMainPanel.add(ScrollPaneFactory.createScrollPane(myFramesList), BorderLayout.CENTER);
    ScrollingUtil.installActions(myFramesList, myMainPanel, false);

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
            XDebugSession session = getSession(e);
            if (session != null) {
              myRefresh = false;
              updateFrames((XExecutionStack)item, session, null, false);
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
        XDebugSession session = getSession(e);
        XSuspendContext context = session == null ? null : session.getSuspendContext();
        if (context != null && !myThreadsCalculated) {
          myBuilder = new ThreadsBuilder();
          context.computeExecutionStacks(myBuilder);
        }
      }
    });
    new ComboboxSpeedSearch(myThreadComboBox) {
      @Override
      protected String getElementText(Object element) {
        return ((XExecutionStack)element).getDisplayName();
      }
    };

    myToolbar = createToolbar();
    myThreadsPanel = new Wrapper();
    myThreadsPanel.setBorder(new CustomLineBorder(CaptionPanel.CNT_ACTIVE_BORDER_COLOR, 0, 0, 1, 0));
    if (myThreadComboBox instanceof XDebuggerEmbeddedComboBox) {
      myToolbar.setOpaque(false);
      ((XDebuggerEmbeddedComboBox<XExecutionStack>)myThreadComboBox).setExtension(myToolbar);
    } else {
      myThreadsPanel.add(myToolbar.getComponent(), BorderLayout.EAST);
    }
    myMainPanel.add(myThreadsPanel, BorderLayout.NORTH);
    myMainPanel.setFocusCycleRoot(true);
    myMainPanel.setFocusTraversalPolicy(new MyFocusPolicy());
    if (Registry.is("debugger.new.tool.window.layout", false) && myMainPanel.getLayout() instanceof BorderLayout) {
      String prev = getShortcutText(IdeActions.ACTION_PREVIOUS_OCCURENCE);
      String next = getShortcutText(IdeActions.ACTION_NEXT_OCCURENCE);
      if (prev != null && next != null) {
        String message = XDebuggerBundle.message("debugger.switch.frames.from.anywhere.hint", prev, next);
        JBLabel hint = new JBLabel(message, UIUtil.ComponentStyle.SMALL);
        hint.setBorder(JBUI.Borders.empty(3, 8));
        hint.setForeground(UIUtil.getContextHelpForeground());
        myMainPanel.add(hint, BorderLayout.SOUTH);
      }
    }
  }

  public void onFrameSelectionKeyPressed(@NotNull Consumer<XStackFrame> handler) {
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

  @Nullable
  @NlsSafe
  private static String getShortcutText(@NotNull @NonNls String actionId) {
    KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut(actionId);
    if (shortcut == null) return null;
    return KeymapUtil.getShortcutText(shortcut);
  }

  private class MyFocusPolicy extends ComponentsListFocusTraversalPolicy {
    @NotNull
    @Override
    protected List<Component> getOrderedComponents() {
      return Arrays.asList(myFramesList,
                           myThreadComboBox);
    }
  }

  public JComponent getDefaultFocusedComponent() {
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

    if (!Registry.is("debugger.new.tool.window.layout", false)) {
      CommonActionsManager actionsManager = CommonActionsManager.getInstance();
      framesGroup.add(actionsManager.createPrevOccurenceAction(myFramesList));
      framesGroup.add(actionsManager.createNextOccurenceAction(myFramesList));
    }
    framesGroup.addAll(ActionManager.getInstance().getAction(XDebuggerActions.FRAMES_TOP_TOOLBAR_GROUP));

    final ActionToolbarImpl toolbar =
      (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, framesGroup, true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.setTargetComponent(myFramesList);
    return toolbar;
  }

  private StackFramesListBuilder getOrCreateBuilder(XExecutionStack executionStack, XDebugSession session) {
    return myBuilders.computeIfAbsent(executionStack, k -> new StackFramesListBuilder(executionStack, session));
  }

  private void withCurrentBuilder(Consumer<? super StackFramesListBuilder> consumer) {
    StackFramesListBuilder builder = myBuilders.get(mySelectedStack);
    if (builder != null) {
      consumer.accept(builder);
    }
  }

  @Override
  public void processSessionEvent(@NotNull SessionEvent event, @NotNull XDebugSession session) {
    myRefresh = event == SessionEvent.SETTINGS_CHANGED;

    if (event == SessionEvent.BEFORE_RESUME) {
      return;
    }

    XExecutionStack currentExecutionStack = ((XDebugSessionImpl)session).getCurrentExecutionStack();
    XStackFrame currentStackFrame = session.getCurrentStackFrame();
    XSuspendContext suspendContext = session.getSuspendContext();

    if (event == SessionEvent.FRAME_CHANGED && Objects.equals(mySelectedStack, currentExecutionStack)) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      if (currentStackFrame != null) {
        myFramesList.setSelectedValue(currentStackFrame, true);
        mySelectedFrameIndex = myFramesList.getSelectedIndex();
        myExecutionStacksWithSelection.put(mySelectedStack, mySelectedFrameIndex);
      }
      return;
    }

    EdtExecutorService.getInstance().execute(() -> {
      if (event != SessionEvent.SETTINGS_CHANGED) {
        mySelectedFrameIndex = 0;
        mySelectedStack = null;
        myVisibleRect = null;
      }
      else {
        myVisibleRect = myFramesList.getVisibleRect();
      }

      myListenersEnabled = false;
      myBuilders.values().forEach(StackFramesListBuilder::dispose);
      myBuilders.clear();

      if (suspendContext == null) {
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
        }
        else {
          myThreadsPanel.add(myThreadComboBox, BorderLayout.CENTER);
        }
        myThreadsPanel.revalidate();
      }
      updateFrames(activeExecutionStack,
                   session,
                   event == SessionEvent.FRAME_CHANGED ? currentStackFrame : null,
                   event == SessionEvent.SETTINGS_CHANGED);
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
        myExecutionStacksWithSelection.put(executionStack, 0);
      }
    }
    return addBeforeSelection;
  }

  private void updateFrames(XExecutionStack executionStack,
                            @NotNull XDebugSession session,
                            @Nullable XStackFrame frameToSelect,
                            boolean refresh) {
    if (mySelectedStack != null) {
      withCurrentBuilder(StackFramesListBuilder::stop);
    }

    mySelectedStack = executionStack;
    if (executionStack != null) {
      mySelectedFrameIndex = myExecutionStacksWithSelection.getInt(executionStack);
      StackFramesListBuilder builder = getOrCreateBuilder(executionStack, session);
      builder.setRefresh(refresh);
      builder.setToSelect(frameToSelect != null ? frameToSelect : mySelectedFrameIndex);
      myListenersEnabled = false;
      boolean selected = builder.initModel(myFramesList.getModel());
      myListenersEnabled = !builder.start() || selected;
    }
  }

  @Override
  public void dispose() {
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  private void processFrameSelection(XDebugSession session, boolean force) {
    mySelectedFrameIndex = myFramesList.getSelectedIndex();
    myExecutionStacksWithSelection.put(mySelectedStack, mySelectedFrameIndex);
    withCurrentBuilder(b -> b.setToSelect(null));

    Object selected = myFramesList.getSelectedValue();
    if (selected instanceof XStackFrame) {
      if (session != null) {
        if (force || (!myRefresh && session.getCurrentStackFrame() != selected)) {
          session.setCurrentStackFrame(mySelectedStack, (XStackFrame)selected, mySelectedFrameIndex == 0);
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
    private boolean myAllFramesLoaded;
    private final XDebugSession mySession;
    private Object myToSelect;
    private boolean myRefresh;

    private StackFramesListBuilder(final XExecutionStack executionStack, XDebugSession session) {
      myExecutionStack = executionStack;
      mySession = session;
      myStackFrames = new ArrayList<>();
    }

    void setToSelect(Object toSelect) {
      myToSelect = toSelect;
    }

    private void setRefresh(boolean refresh) {
      myRefresh = refresh;
    }

    @Override
    public void addStackFrames(@NotNull final List<? extends XStackFrame> stackFrames, final boolean last) {
      addStackFrames(stackFrames, null, last);
    }

    @Override
    public void addStackFrames(@NotNull final List<? extends XStackFrame> stackFrames, @Nullable XStackFrame toSelect, final boolean last) {
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
          myRunning = false;
          myListenersEnabled = true;
        }
      });
    }

    @Override
    public void errorOccurred(@NotNull final String errorMessage) {
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

    @Override
    public boolean isObsolete() {
      return !myRunning;
    }

    public void dispose() {
      myRunning = false;
      myExecutionStack = null;
    }

    public boolean start() {
      if (myExecutionStack == null || myErrorMessage != null) {
        return false;
      }
      myRunning = true;
      myExecutionStack.computeStackFrames(myNextFrameIndex, this);
      return true;
    }

    public void stop() {
      myRunning = false;
    }

    private boolean selectCurrentFrame() {
      if (selectFrame(myToSelect)) {
        myListenersEnabled = true;
        processFrameSelection(mySession, false);
        return true;
      }
      return false;
    }

    private boolean selectFrame(Object toSelect) {
      if (toSelect instanceof XStackFrame) {
        if (myFramesList.selectFrame((XStackFrame)toSelect)) return true;
        if (myAllFramesLoaded && myFramesList.getSelectedValue() == null) {
          LOG.error("Frame was not found, " + myToSelect.getClass() + " must correctly override equals");
        }
      }
      else if (toSelect instanceof Integer) {
        if (myFramesList.selectFrame((int)toSelect)) return true;
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
}
