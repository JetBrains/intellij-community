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

import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.HashMap;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
public class XFramesView extends XDebugViewBase {
  private final JPanel myMainPanel;
  private final XDebuggerFramesList myFramesList;
  private final JComboBox myThreadComboBox;
  private final Set<XExecutionStack> myExecutionStacks = Sets.newHashSet();
  private XExecutionStack mySelectedStack;
  private boolean myListenersEnabled;
  private final Map<XExecutionStack, StackFramesListBuilder> myBuilders = new HashMap<XExecutionStack, StackFramesListBuilder>();

  public XFramesView(final XDebugSession session, final Disposable parentDisposable) {
    super(session, parentDisposable);

    myMainPanel = new JPanel(new BorderLayout());

    myThreadComboBox = new JComboBox();
    myThreadComboBox.setRenderer(new ThreadComboBoxRenderer());
    myThreadComboBox.addItemListener(new MyItemListener());
    myMainPanel.add(myThreadComboBox, BorderLayout.NORTH);

    myFramesList = new XDebuggerFramesList(session.getProject());
    myFramesList.addListSelectionListener(new ListSelectionListener() {
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
    rebuildView(SessionEvent.RESUMED);
  }

  private StackFramesListBuilder getOrCreateBuilder(XExecutionStack executionStack) {
    StackFramesListBuilder builder = myBuilders.get(executionStack);
    if (builder == null) {
      builder = new StackFramesListBuilder(executionStack);
      myBuilders.put(executionStack, builder);
    }
    return builder;
  }

  protected void rebuildView(final SessionEvent event) {
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
        myThreadComboBox.addItem(executionStack);
        myExecutionStacks.add(executionStack);
      }
    }
    XExecutionStack activeExecutionStack = suspendContext.getActiveExecutionStack();
    myThreadComboBox.setSelectedItem(activeExecutionStack);
    myThreadComboBox.setVisible(executionStacks.length != 1);
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
        onFrameSelected(topFrame);
      }
    }
  }

  public XDebuggerFramesList getFramesList() {
    return myFramesList;
  }

  private void onFrameSelected(final @NotNull XStackFrame stackFrame) {
    mySession.setCurrentStackFrame(stackFrame);
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  private void processFrameSelection() {
    if (!myListenersEnabled) return;
    Object selected = myFramesList.getSelectedValue();
    if (selected instanceof XStackFrame) {
      onFrameSelected((XStackFrame)selected);
    }
  }

  private class MyItemListener implements ItemListener {
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
      myStackFrames.add(executionStack.getTopFrame());
      myNextFrameIndex = 1;
    }

    public void addStackFrames(@NotNull final List<? extends XStackFrame> stackFrames, final boolean last) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
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

    private void addFrameListElements(final List<?> values, final boolean last) {
      if (myExecutionStack != null && myExecutionStack == mySelectedStack) {
        DefaultListModel model = myFramesList.getModel();
        if (model.getElementAt(model.getSize() - 1) == null) {
          model.removeElementAt(model.getSize() - 1);
        }
        for (Object value : values) {
          model.addElement(value);
        }
        if (!last) {
          model.addElement(null);
        }
        myFramesList.repaint();
      }
    }

    public boolean isObsolete() {
      return !myRunning;
    }

    public void errorOccured(final String errorMessage) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myErrorMessage = errorMessage;
          addFrameListElements(Collections.singletonList(errorMessage), true);
          myRunning = false;
        }
      });
    }

    public void dispose() {
      myRunning = false;
      myExecutionStack = null;
    }

    public void start() {
      if (myExecutionStack == null) {
        return;
      }
      myRunning = true;
      myExecutionStack.computeStackFrames(myNextFrameIndex, this);
    }

    public void stop() {
      myRunning = false;
    }

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
