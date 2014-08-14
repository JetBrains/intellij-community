/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
  private boolean myListenersEnabled;
  private final Map<XExecutionStack, StackFramesListBuilder> myBuilders = new HashMap<XExecutionStack, StackFramesListBuilder>();
  private final ActionToolbarImpl myToolbar;
  private final Wrapper myThreadsPanel;
  private boolean myThreadsCalculated = false;
  private final TransferToEDTQueue<Runnable> myLaterInvocator = TransferToEDTQueue.createRunnableMerger("XFramesView later invocator", 50);

  public XFramesView(@NotNull Project project) {
    myMainPanel = new JPanel(new BorderLayout());

    myFramesList = new XDebuggerFramesList(project);
    myFramesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (myListenersEnabled && !e.getValueIsAdjusting()) {
          processFrameSelection(e);
        }
      }
    });
    myFramesList.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (myListenersEnabled) {
          int i = myFramesList.locationToIndex(e.getPoint());
          if (i != -1 && myFramesList.isSelectedIndex(i)) {
            processFrameSelection(e);
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
          if (item instanceof XExecutionStack) {
            XDebugSession session = getSession(e);
            if (session != null) {
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
    framesGroup.add(actionsManager.createPrevOccurenceAction(getFramesList()));
    framesGroup.add(actionsManager.createNextOccurenceAction(getFramesList()));

    framesGroup.addAll(ActionManager.getInstance().getAction(XDebuggerActions.FRAMES_TOP_TOOLBAR_GROUP));

    final ActionToolbarImpl toolbar =
      (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, framesGroup, true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.setAddSeparatorFirst(true);
    toolbar.getComponent().setBorder(new EmptyBorder(1, 0, 0, 0));
    return toolbar;
  }

  private StackFramesListBuilder getOrCreateBuilder(XExecutionStack executionStack) {
    StackFramesListBuilder builder = myBuilders.get(executionStack);
    if (builder == null) {
      builder = new StackFramesListBuilder(executionStack);
      myBuilders.put(executionStack, builder);
    }
    return builder;
  }

  @Override
  public void processSessionEvent(@NotNull final SessionEvent event) {
    if (event == SessionEvent.BEFORE_RESUME) {
      return;
    }

    XDebugSession session = getSession(getMainPanel());

    if (event == SessionEvent.FRAME_CHANGED) {
      XStackFrame currentStackFrame = session == null ? null : session.getCurrentStackFrame();
      if (currentStackFrame != null) {
        myFramesList.setSelectedValue(currentStackFrame, true);
      }
      return;
    }

    myListenersEnabled = false;
    for (StackFramesListBuilder builder : myBuilders.values()) {
      builder.dispose();
    }
    myBuilders.clear();
    mySelectedStack = null;
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

    XExecutionStack activeExecutionStack = suspendContext.getActiveExecutionStack();
    myThreadComboBox.setSelectedItem(activeExecutionStack);
    myThreadsPanel.removeAll();
    myThreadsPanel.add(myToolbar.getComponent(), BorderLayout.EAST);
    final boolean invisible = executionStacks.length == 1 && StringUtil.isEmpty(executionStacks[0].getDisplayName());
    if (!invisible) {
      myThreadsPanel.add(myThreadComboBox, BorderLayout.CENTER);
    }
    myToolbar.setAddSeparatorFirst(!invisible);
    updateFrames(activeExecutionStack, session);
    myListenersEnabled = true;
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
    if (mySelectedStack == executionStack) {
      return;
    }
    if (mySelectedStack != null) {
      getOrCreateBuilder(mySelectedStack).stop();
    }

    mySelectedStack = executionStack;
    if (executionStack != null) {
      StackFramesListBuilder builder = getOrCreateBuilder(executionStack);
      builder.initModel(myFramesList.getModel());
      builder.start();
      XStackFrame topFrame = executionStack.getTopFrame();
      if (topFrame != null) {
        myFramesList.setSelectedValue(topFrame, true);
        session.setCurrentStackFrame(executionStack, topFrame);
      }
    }
  }

  @Override
  public void dispose() {
  }

  public XDebuggerFramesList getFramesList() {
    return myFramesList;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  private void processFrameSelection(@NotNull EventObject e) {
    Object selected = myFramesList.getSelectedValue();
    if (selected instanceof XStackFrame) {
      XDebugSession session = getSession(e);
      if (session != null) {
        session.setCurrentStackFrame(mySelectedStack, (XStackFrame)selected);
      }
    }
  }

  private class StackFramesListBuilder implements XExecutionStack.XStackFrameContainer {
    private XExecutionStack myExecutionStack;
    private final List<XStackFrame> myStackFrames;
    private String myErrorMessage;
    private int myNextFrameIndex;
    private boolean myRunning;
    private boolean myAllFramesLoaded;

    private StackFramesListBuilder(final XExecutionStack executionStack) {
      myExecutionStack = executionStack;
      myStackFrames = new ArrayList<XStackFrame>();
      XStackFrame topFrame = executionStack.getTopFrame();
      if (topFrame != null) {
        myStackFrames.add(topFrame);
        myNextFrameIndex = 1;
      }
      else {
        myNextFrameIndex = 0;
      }
    }

    @Override
    public void addStackFrames(@NotNull final List<? extends XStackFrame> stackFrames, final boolean last) {
      myLaterInvocator.offer(new Runnable() {
        @Override
        public void run() {
          myStackFrames.addAll(stackFrames);
          addFrameListElements(stackFrames, last);
          myNextFrameIndex += stackFrames.size();
          myAllFramesLoaded = last;
          if (last) {
            myRunning = false;
          }
        }
      });
    }

    @Override
    public void errorOccurred(@NotNull final String errorMessage) {
      myLaterInvocator.offer(new Runnable() {
        @Override
        public void run() {
          if (myErrorMessage == null) {
            myErrorMessage = errorMessage;
            addFrameListElements(Collections.singletonList(errorMessage), true);
            myRunning = false;
          }
        }
      });
    }

    private void addFrameListElements(final List<?> values, final boolean last) {
      if (myExecutionStack != null && myExecutionStack == mySelectedStack) {
        DefaultListModel model = myFramesList.getModel();
        if (!model.isEmpty() && model.getElementAt(model.getSize() - 1) == null) {
          model.removeElementAt(model.getSize() - 1);
        }
        for (Object value : values) {
          //noinspection unchecked
          model.addElement(value);
        }
        if (!last) {
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

    public void start() {
      if (myExecutionStack == null || myErrorMessage != null) {
        return;
      }
      myRunning = true;
      myExecutionStack.computeStackFrames(myNextFrameIndex, this);
    }

    public void stop() {
      myRunning = false;
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
    }
  }
}
