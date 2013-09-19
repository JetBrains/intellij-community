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
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CaptionPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
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
public class XFramesView implements XDebugView {
  private final JPanel myMainPanel;
  private final XDebuggerFramesList myFramesList;
  private final JComboBox myThreadComboBox;
  private final Set<XExecutionStack> myExecutionStacks = ContainerUtil.newHashSet();
  @NotNull private final XDebugSession mySession;
  private XExecutionStack mySelectedStack;
  private boolean myListenersEnabled;
  private final Map<XExecutionStack, StackFramesListBuilder> myBuilders = new HashMap<XExecutionStack, StackFramesListBuilder>();
  private final ActionToolbarImpl myToolbar;
  private final Wrapper myThreadsPanel;

  public XFramesView(@NotNull final XDebugSession session) {
    mySession = session;

    myMainPanel = new JPanel(new BorderLayout());

    myFramesList = new XDebuggerFramesList(session.getProject());
    myFramesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        processFrameSelection();
      }
    });
    myFramesList.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        int i = myFramesList.locationToIndex(e.getPoint());
        if (i != -1 && myFramesList.isSelectedIndex(i)) {
          processFrameSelection();
        }
      }
    });
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myFramesList), BorderLayout.CENTER);

    myThreadComboBox = new JComboBox();
    //noinspection unchecked
    myThreadComboBox.setRenderer(new ThreadComboBoxRenderer(myThreadComboBox));
    myThreadComboBox.addItemListener(new MyItemListener());
    myToolbar = createToolbar();
    myThreadsPanel = new Wrapper();
    CustomLineBorder border = new CustomLineBorder(CaptionPanel.CNT_ACTIVE_BORDER_COLOR, 0, 0, 1, 0);
    myThreadsPanel.setBorder(border);
    myThreadsPanel.add(myToolbar.getComponent(), BorderLayout.EAST);
    myMainPanel.add(myThreadsPanel, BorderLayout.NORTH);

    processSessionEvent(SessionEvent.RESUMED);
  }

  private ActionToolbarImpl createToolbar() {
    final DefaultActionGroup framesGroup = new DefaultActionGroup();

    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    framesGroup.add(actionsManager.createPrevOccurenceAction(getFramesList()));
    framesGroup.add(actionsManager.createNextOccurenceAction(getFramesList()));

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
    if (event == SessionEvent.BEFORE_RESUME) return;
    if (event == SessionEvent.FRAME_CHANGED) {
      XStackFrame currentStackFrame = mySession.getCurrentStackFrame();
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
    XSuspendContext suspendContext = mySession.getSuspendContext();
    if (suspendContext == null) {
      myThreadComboBox.removeAllItems();
      myFramesList.clear();
      myExecutionStacks.clear();
      return;
    }

    XExecutionStack[] executionStacks = suspendContext.getExecutionStacks();
    for (XExecutionStack executionStack : executionStacks) {
      if (!myExecutionStacks.contains(executionStack)) {
        //noinspection unchecked
        myThreadComboBox.addItem(executionStack);
        myExecutionStacks.add(executionStack);
      }
    }
    XExecutionStack activeExecutionStack = suspendContext.getActiveExecutionStack();
    myThreadComboBox.setSelectedItem(activeExecutionStack);
    myThreadsPanel.removeAll();
    myThreadsPanel.add(myToolbar.getComponent(), BorderLayout.EAST);
    final boolean invisible = executionStacks.length == 1 && StringUtil.isEmpty(executionStacks[0].getDisplayName());
    if (!invisible) {
      myThreadsPanel.add(myThreadComboBox, BorderLayout.CENTER);
    }
    myToolbar.setAddSeparatorFirst(!invisible);
    updateFrames(activeExecutionStack);
    myListenersEnabled = true;
  }

  private void updateFrames(final XExecutionStack executionStack) {
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
        onFrameSelected(executionStack, topFrame);
      }
    }
  }

  @Override
  public void dispose() {
  }

  public XDebuggerFramesList getFramesList() {
    return myFramesList;
  }

  private void onFrameSelected(XExecutionStack executionStack, final @NotNull XStackFrame stackFrame) {
    mySession.setCurrentStackFrame(executionStack, stackFrame);
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  private void processFrameSelection() {
    if (!myListenersEnabled) return;
    Object selected = myFramesList.getSelectedValue();
    if (selected instanceof XStackFrame) {
      onFrameSelected(mySelectedStack, (XStackFrame)selected);
    }
  }

  private class MyItemListener implements ItemListener {
    @Override
    public void itemStateChanged(final ItemEvent e) {
      if (!myListenersEnabled) return;

      if (e.getStateChange() == ItemEvent.SELECTED) {
        Object item = e.getItem();
        if (item instanceof XExecutionStack) {
          updateFrames((XExecutionStack)item);
        }
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
      }
      myNextFrameIndex = 1;
    }

    @Override
    public void addStackFrames(@NotNull final List<? extends XStackFrame> stackFrames, final boolean last) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
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
      ApplicationManager.getApplication().invokeLater(new Runnable() {
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
        if (model.getElementAt(model.getSize() - 1) == null) {
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
