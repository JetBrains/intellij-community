// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame;

import com.intellij.CommonBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

public class XFramesView extends XDebugView {
  private static final Logger LOG = Logger.getInstance(XFramesView.class);

  private final JPanel myMainPanel;
  private final XDebuggerFramesList myFramesList;
  private final ComboBox<XExecutionStack> myThreadComboBox;
  private final TObjectIntHashMap<XExecutionStack> myExecutionStacksWithSelection = new TObjectIntHashMap<>();
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

    myFramesList = new XDebuggerFramesList(project);
    myFramesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (myListenersEnabled && !e.getValueIsAdjusting() && mySelectedFrameIndex != myFramesList.getSelectedIndex()) {
          processFrameSelection(getSession(e), true);
        }
      }
    });
    myFramesList.addMouseListener(new MouseAdapter() {
      // not mousePressed here, otherwise click in unfocused frames list transfers focus to the new opened editor
      @Override
      public void mouseReleased(final MouseEvent e) {
        if (myListenersEnabled) {
          int i = myFramesList.locationToIndex(e.getPoint());
          if (i != -1 && myFramesList.isSelectedIndex(i)) {
            processFrameSelection(getSession(e), true);
          }
        }
      }
    });

    myFramesList.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        ActionManager actionManager = ActionManager.getInstance();
        ActionGroup group = (ActionGroup)actionManager.getAction(XDebuggerActions.FRAMES_TREE_POPUP_GROUP);
        actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group).getComponent().show(comp, x, y);
      }
    });

    myMainPanel.add(ScrollPaneFactory.createScrollPane(myFramesList), BorderLayout.CENTER);

    myThreadComboBox = new ComboBox<>();
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
    myThreadsPanel.add(myToolbar.getComponent(), BorderLayout.EAST);
    myMainPanel.add(myThreadsPanel, BorderLayout.NORTH);
    myMainPanel.setFocusCycleRoot(true);
    myMainPanel.setFocusTraversalPolicy(new MyFocusPolicy());
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
      ArrayList<? extends XExecutionStack> copyStacks = new ArrayList<>(executionStacks); // to capture the current List elements
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

    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    framesGroup.add(actionsManager.createPrevOccurenceAction(myFramesList));
    framesGroup.add(actionsManager.createNextOccurenceAction(myFramesList));

    framesGroup.addAll(ActionManager.getInstance().getAction(XDebuggerActions.FRAMES_TOP_TOOLBAR_GROUP));

    final ActionToolbarImpl toolbar =
      (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, framesGroup, true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    return toolbar;
  }

  private StackFramesListBuilder getOrCreateBuilder(XExecutionStack executionStack, XDebugSession session) {
    return myBuilders.computeIfAbsent(executionStack, k -> new StackFramesListBuilder(executionStack, session));
  }

  private void withCurrentBuilder(Consumer<? super StackFramesListBuilder> consumer) {
    StackFramesListBuilder builder = myBuilders.get(mySelectedStack);
    if (builder != null) {
      consumer.consume(builder);
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
      if (!myExecutionStacksWithSelection.contains(executionStack)) {
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
      mySelectedFrameIndex = myExecutionStacksWithSelection.get(executionStack);
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

  private class StackFramesListBuilder implements XStackFrameContainerEx {
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
      if (myToSelect instanceof XStackFrame) {
        if (!Objects.equals(myFramesList.getSelectedValue(), myToSelect) && myFramesList.getModel().contains(myToSelect)) {
          myFramesList.setSelectedValue(myToSelect, true);
          processFrameSelection(mySession, false);
          myListenersEnabled = true;
          return true;
        }
        if (myAllFramesLoaded && myFramesList.getSelectedValue() == null) {
          LOG.error("Frame was not found, " + myToSelect.getClass() + " must correctly override equals");
        }
      }
      else if (myToSelect instanceof Integer) {
        int selectedFrameIndex = (int)myToSelect;
        if (myFramesList.getSelectedIndex() != selectedFrameIndex &&
            myFramesList.getElementCount() > selectedFrameIndex &&
            myFramesList.getModel().getElementAt(selectedFrameIndex) != null) {
          myFramesList.setSelectedIndex(selectedFrameIndex);
          processFrameSelection(mySession, false);
          myListenersEnabled = true;
          return true;
        }
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
