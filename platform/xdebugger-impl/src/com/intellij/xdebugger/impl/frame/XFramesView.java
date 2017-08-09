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
package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.TransferToEDTQueue;
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
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class XFramesView extends XDebugView {
  private static final Logger LOG = Logger.getInstance(XFramesView.class);
  public static final DataKey<XFramesView> DATA_KEY = DataKey.create("XDEBUGGER_FRAMES_VIEW");

  private final JPanel myMainPanel;
  private final XDebuggerFramesList myFramesList;
  private final ComboBox myThreadComboBox;
  private final TObjectIntHashMap<XExecutionStack> myExecutionStacksWithSelection = new TObjectIntHashMap<>();
  private XExecutionStack mySelectedStack;
  private int mySelectedFrameIndex;
  private Rectangle myVisibleRect;
  private boolean myListenersEnabled;
  private final Map<XExecutionStack, StackFramesListBuilder> myBuilders = new HashMap<>();
  private final ActionToolbarImpl myToolbar;
  private final Wrapper myThreadsPanel;
  private boolean myThreadsCalculated = false;
  private final TransferToEDTQueue<Runnable> myLaterInvocator = TransferToEDTQueue.createRunnableMerger("XFramesView later invocator", 50);
  private boolean myRefresh = false;

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
      @Override
      public void mousePressed(final MouseEvent e) {
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

    myThreadComboBox = new ComboBox();
    //noinspection unchecked
    myThreadComboBox.setRenderer(new ThreadComboBoxRenderer(myThreadComboBox));
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
              updateFrames((XExecutionStack)item, session, null);
            }
          }
        }
      }
    });
    myThreadComboBox.addPopupMenuListener(new PopupMenuListenerAdapter() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        XDebugSession session = getSession(e);
        XSuspendContext context = session == null ? null : session.getSuspendContext();
        if (context != null && !myThreadsCalculated) {
          myThreadsCalculated = true;
          //noinspection unchecked
          myThreadComboBox.addItem(null); // rendered as "Loading..."
          context.computeExecutionStacks(new XSuspendContext.XExecutionStackContainer() {
            @Override
            public void addExecutionStack(@NotNull final List<? extends XExecutionStack> executionStacks, boolean last) {
              ApplicationManager.getApplication().invokeLater(() -> {
                addExecutionStacks(executionStacks);
                if (last) {
                  myThreadComboBox.removeItem(null);
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
            }
          });
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
  }

  public void selectFrame(XExecutionStack stack, XStackFrame frame) {
    myThreadComboBox.setSelectedItem(stack);

    myLaterInvocator.offer(() ->
      myFramesList.setSelectedValue(frame, true)
    );
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
    toolbar.setAddSeparatorFirst(true);
    return toolbar;
  }

  private StackFramesListBuilder getOrCreateBuilder(XExecutionStack executionStack, XDebugSession session) {
    return myBuilders.computeIfAbsent(executionStack, k -> new StackFramesListBuilder(executionStack, session));
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

    myLaterInvocator.offer(() -> {
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
      addExecutionStacks(Collections.singletonList(activeExecutionStack));

      XExecutionStack[] executionStacks = suspendContext.getExecutionStacks();
      addExecutionStacks(Arrays.asList(executionStacks));

      myThreadComboBox.setSelectedItem(activeExecutionStack);
      myThreadsPanel.removeAll();
      myThreadsPanel.add(myToolbar.getComponent(), BorderLayout.EAST);
      final boolean invisible = executionStacks.length == 1 && StringUtil.isEmpty(executionStacks[0].getDisplayName());
      if (!invisible) {
        myThreadsPanel.add(myThreadComboBox, BorderLayout.CENTER);
      }
      myToolbar.setAddSeparatorFirst(!invisible);
      updateFrames(activeExecutionStack, session, event == SessionEvent.FRAME_CHANGED ? currentStackFrame : null);
    });
  }

  @Override
  protected void clear() {
    myThreadComboBox.removeAllItems();
    myFramesList.clear();
    myThreadsCalculated = false;
    myExecutionStacksWithSelection.clear();
  }

  private void addExecutionStacks(List<? extends XExecutionStack> executionStacks) {
    for (XExecutionStack executionStack : executionStacks) {
      if (!myExecutionStacksWithSelection.contains(executionStack)) {
        //noinspection unchecked
        myThreadComboBox.addItem(executionStack);
        myExecutionStacksWithSelection.put(executionStack, 0);
      }
    }
  }

  private void updateFrames(XExecutionStack executionStack, @NotNull XDebugSession session, @Nullable XStackFrame frameToSelect) {
    if (mySelectedStack != null) {
      getOrCreateBuilder(mySelectedStack, session).stop();
    }

    mySelectedStack = executionStack;
    if (executionStack != null) {
      mySelectedFrameIndex = myExecutionStacksWithSelection.get(executionStack);
      StackFramesListBuilder builder = getOrCreateBuilder(executionStack, session);
      builder.setToSelect(frameToSelect != null ? frameToSelect : mySelectedFrameIndex);
      myListenersEnabled = false;
      builder.initModel(myFramesList.getModel());
      myListenersEnabled = !builder.start();
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
    private int myNextFrameIndex = 0;
    private volatile boolean myRunning;
    private boolean myAllFramesLoaded;
    private final XDebugSession mySession;
    private Object myToSelect;

    private StackFramesListBuilder(final XExecutionStack executionStack, XDebugSession session) {
      myExecutionStack = executionStack;
      mySession = session;
      myStackFrames = new ArrayList<>();
    }

    void setToSelect(Object toSelect) {
      myToSelect = toSelect;
    }

    @Override
    public void addStackFrames(@NotNull final List<? extends XStackFrame> stackFrames, final boolean last) {
      addStackFrames(stackFrames, null, last);
    }
    
    @Override
    public void addStackFrames(@NotNull final List<? extends XStackFrame> stackFrames, @Nullable XStackFrame toSelect, final boolean last) {
      if (isObsolete()) return;
      myLaterInvocator.offer(() -> {
        if (isObsolete()) return;
        myStackFrames.addAll(stackFrames);
        addFrameListElements(stackFrames, last);

        if (toSelect != null) {
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
      myLaterInvocator.offer(() -> {
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
        DefaultListModel model = myFramesList.getModel();
        int insertIndex = model.size();
        boolean loadingPresent = !model.isEmpty() && model.getElementAt(model.getSize() - 1) == null;
        if (loadingPresent) {
          insertIndex--;
        }
        for (Object value : values) {
          //noinspection unchecked
          model.add(insertIndex++, value);
        }
        if (last) {
          if (loadingPresent) {
            model.removeElementAt(model.getSize() - 1);
          }
        }
        else if (!loadingPresent) {
          //noinspection unchecked
          model.addElement(null);
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

    private void selectCurrentFrame() {
      if (myToSelect instanceof XStackFrame) {
        if (!Objects.equals(myFramesList.getSelectedValue(), myToSelect) && myFramesList.getModel().contains(myToSelect)) {
          myFramesList.setSelectedValue(myToSelect, true);
          processFrameSelection(mySession, false);
          myListenersEnabled = true;
        }
        if (myAllFramesLoaded && myFramesList.getSelectedValue() == null) {
          LOG.error("Frame was not found, " + myToSelect.getClass() + " must correctly override equals");
        }
      }
      else if (myToSelect instanceof Integer) {
        int selectedFrameIndex = (int)myToSelect;
        if (myFramesList.getSelectedIndex() != selectedFrameIndex &&
            myFramesList.getElementCount() > selectedFrameIndex &&
            myFramesList.getModel().get(selectedFrameIndex) != null) {
          myFramesList.setSelectedIndex(selectedFrameIndex);
          processFrameSelection(mySession, false);
          myListenersEnabled = true;
        }
      }
    }

    @SuppressWarnings("unchecked")
    public void initModel(final DefaultListModel model) {
      model.removeAllElements();
      myStackFrames.forEach(model::addElement);
      if (myErrorMessage != null) {
        model.addElement(myErrorMessage);
      }
      else if (!myAllFramesLoaded) {
        model.addElement(null);
      }
      selectCurrentFrame();
    }
  }
}
