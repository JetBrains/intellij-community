/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
  private final JPanel myMainPanel;
  private final XDebuggerFramesList myFramesList;
  private final ComboBox myThreadComboBox;
  private final Set<XExecutionStack> myExecutionStacks = ContainerUtil.newHashSet();
  private XExecutionStack mySelectedStack;
  private int mySelectedFrameIndex;
  private Rectangle myVisibleRect;
  private boolean myListenersEnabled;
  private final Map<XExecutionStack, StackFramesListBuilder> myBuilders = new HashMap<XExecutionStack, StackFramesListBuilder>();
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
              mySelectedFrameIndex = 0;
              myRefresh = false;
              updateFrames((XExecutionStack)item, session);
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
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  myThreadComboBox.removeItem(null);
                  addExecutionStacks(executionStacks);
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
    toolbar.getComponent().setBorder(new EmptyBorder(1, 0, 0, 0));
    return toolbar;
  }

  private StackFramesListBuilder getOrCreateBuilder(XExecutionStack executionStack, XDebugSession session) {
    StackFramesListBuilder builder = myBuilders.get(executionStack);
    if (builder == null) {
      builder = new StackFramesListBuilder(executionStack, session);
      myBuilders.put(executionStack, builder);
    }
    return builder;
  }

  @Override
  public void processSessionEvent(@NotNull final SessionEvent event) {
    myRefresh = event == SessionEvent.SETTINGS_CHANGED;

    if (event == SessionEvent.BEFORE_RESUME) {
      return;
    }

    XDebugSession session = getSession(getMainPanel());

    if (event == SessionEvent.FRAME_CHANGED) {
      XStackFrame currentStackFrame = session == null ? null : session.getCurrentStackFrame();
      if (currentStackFrame != null) {
        myFramesList.setSelectedValue(currentStackFrame, true);
        mySelectedFrameIndex = myFramesList.getSelectedIndex();
      }
      return;
    }

    if (event != SessionEvent.SETTINGS_CHANGED) {
      mySelectedFrameIndex = 0;
      mySelectedStack = null;
      myVisibleRect = null;
    }
    else {
      myVisibleRect = myFramesList.getVisibleRect();
    }

    myListenersEnabled = false;
    for (StackFramesListBuilder builder : myBuilders.values()) {
      builder.dispose();
    }
    myBuilders.clear();
    XSuspendContext suspendContext = session == null ? null : session.getSuspendContext();
    if (suspendContext == null) {
      requestClear();
      return;
    }

    if (event == SessionEvent.PAUSED) {
      // clear immediately
      cancelClear();
      clear();
    }

    XExecutionStack[] executionStacks = suspendContext.getExecutionStacks();
    addExecutionStacks(Arrays.asList(executionStacks));

    XExecutionStack activeExecutionStack = mySelectedStack != null ? mySelectedStack : suspendContext.getActiveExecutionStack();
    myThreadComboBox.setSelectedItem(activeExecutionStack);
    myThreadsPanel.removeAll();
    myThreadsPanel.add(myToolbar.getComponent(), BorderLayout.EAST);
    final boolean invisible = executionStacks.length == 1 && StringUtil.isEmpty(executionStacks[0].getDisplayName());
    if (!invisible) {
      myThreadsPanel.add(myThreadComboBox, BorderLayout.CENTER);
    }
    myToolbar.setAddSeparatorFirst(!invisible);
    updateFrames(activeExecutionStack, session);
  }

  @Override
  protected void clear() {
    myThreadComboBox.removeAllItems();
    myFramesList.clear();
    myThreadsCalculated = false;
    myExecutionStacks.clear();
  }

  private void addExecutionStacks(List<? extends XExecutionStack> executionStacks) {
    for (XExecutionStack executionStack : executionStacks) {
      if (!myExecutionStacks.contains(executionStack)) {
        //noinspection unchecked
        myThreadComboBox.addItem(executionStack);
        myExecutionStacks.add(executionStack);
      }
    }
  }

  private void updateFrames(final XExecutionStack executionStack, @NotNull XDebugSession session) {
    if (mySelectedStack != null) {
      getOrCreateBuilder(mySelectedStack, session).stop();
    }

    mySelectedStack = executionStack;
    if (executionStack != null) {
      StackFramesListBuilder builder = getOrCreateBuilder(executionStack, session);
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
    Object selected = myFramesList.getSelectedValue();
    if (selected instanceof XStackFrame) {
      if (session != null) {
        if (force || (!myRefresh && session.getCurrentStackFrame() != selected)) {
          session.setCurrentStackFrame(mySelectedStack, (XStackFrame)selected, mySelectedFrameIndex == 0);
        }
      }
    }
  }

  private class StackFramesListBuilder implements XExecutionStack.XStackFrameContainer {
    private XExecutionStack myExecutionStack;
    private final List<XStackFrame> myStackFrames;
    private String myErrorMessage;
    private int myNextFrameIndex = 0;
    private volatile boolean myRunning;
    private boolean myAllFramesLoaded;
    private final XDebugSession mySession;

    private StackFramesListBuilder(final XExecutionStack executionStack, XDebugSession session) {
      myExecutionStack = executionStack;
      mySession = session;
      myStackFrames = new ArrayList<XStackFrame>();
    }

    @Override
    public void addStackFrames(@NotNull final List<? extends XStackFrame> stackFrames, final boolean last) {
      if (isObsolete()) return;
      myLaterInvocator.offer(new Runnable() {
        @Override
        public void run() {
          if (isObsolete()) return;
          myStackFrames.addAll(stackFrames);
          addFrameListElements(stackFrames, last);
          selectCurrentFrame();
          myNextFrameIndex += stackFrames.size();
          myAllFramesLoaded = last;
          if (last) {
            if (myVisibleRect != null) {
              myFramesList.scrollRectToVisible(myVisibleRect);
            }
            myRunning = false;
            myListenersEnabled = true;
          }
        }
      });
    }

    @Override
    public void errorOccurred(@NotNull final String errorMessage) {
      if (isObsolete()) return;
      myLaterInvocator.offer(new Runnable() {
        @Override
        public void run() {
          if (isObsolete()) return;
          if (myErrorMessage == null) {
            myErrorMessage = errorMessage;
            addFrameListElements(Collections.singletonList(errorMessage), true);
            myRunning = false;
            myListenersEnabled = true;
          }
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
      if (mySelectedStack != null &&
          myFramesList.getSelectedIndex() != mySelectedFrameIndex &&
          myFramesList.getElementCount() > mySelectedFrameIndex &&
          myFramesList.getModel().get(mySelectedFrameIndex) != null) {
        myFramesList.setSelectedIndex(mySelectedFrameIndex);
        processFrameSelection(mySession, false);
        myListenersEnabled = true;
      }
    }

    @SuppressWarnings("unchecked")
    public void initModel(final DefaultListModel model) {
      model.removeAllElements();
      for (XStackFrame stackFrame : myStackFrames) {
        model.addElement(stackFrame);
      }
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
