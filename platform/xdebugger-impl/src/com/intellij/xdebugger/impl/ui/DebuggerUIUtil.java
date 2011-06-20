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
package com.intellij.xdebugger.impl.ui;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.breakpoints.ui.XLightBreakpointPropertiesPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: lex
 * Date: Sep 20, 2003
 * Time: 11:26:44 PM
 */
public class DebuggerUIUtil {
  @NonNls public static final String FULL_VALUE_POPUP_DIMENSION_KEY = "XDebugger.FullValuePopup";

  private DebuggerUIUtil() {
  }

  public static void enableEditorOnCheck(final JCheckBox checkbox, final JComponent textfield) {
    checkbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean selected = checkbox.isSelected();
        textfield.setEnabled(selected);
      }
    });
    textfield.setEnabled(checkbox.isSelected());
  }

  public static void focusEditorOnCheck(final JCheckBox checkbox, final JComponent component) {
    final Runnable runnable = new Runnable() {
      public void run() {
        component.requestFocus();
      }
    };
    checkbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (checkbox.isSelected()) {
          SwingUtilities.invokeLater(runnable);
        }
      }
    });
  }

  public static void invokeLater(final Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable);
  }

  public static void invokeOnEventDispatch(final Runnable runnable) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
  }

  public static RelativePoint calcPopupLocation(Editor editor, final int line) {
    Point p = editor.logicalPositionToXY(new LogicalPosition(line + 1, 0));

    final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    if (!visibleArea.contains(p)) {
      p = new Point((visibleArea.x + visibleArea.width) / 2, (visibleArea.y + visibleArea.height) / 2);
    }
    return new RelativePoint(editor.getContentComponent(), p);
  }

  public static void showValuePopup(@NotNull XFullValueEvaluator text, @NotNull MouseEvent event, @NotNull Project project) {
    final JTextArea textArea = new JTextArea("Evaluating...");
    final FullValueEvaluationCallbackImpl callback = new FullValueEvaluationCallbackImpl(textArea);
    text.startEvaluation(callback);
    textArea.setEditable(false);
    textArea.setBackground(HintUtil.INFORMATION_COLOR);
    textArea.setLineWrap(false);

    final JScrollPane component = ScrollPaneFactory.createScrollPane(textArea);
    final Dimension frameSize = WindowManager.getInstance().getFrame(project).getSize();
    Dimension size = DimensionService.getInstance().getSize(FULL_VALUE_POPUP_DIMENSION_KEY, project);
    if (size == null) {
      size = new Dimension(frameSize.width / 2, frameSize.height / 2);
    }

    component.setPreferredSize(size);
    component.setBorder(null);

    final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(project, FULL_VALUE_POPUP_DIMENSION_KEY, false)
      .setRequestFocus(false)
      .setCancelCallback(new Computable<Boolean>() {
        public Boolean compute() {
          callback.setObsolete();
          return true;
        }
      })
      .createPopup();
    final Component parentComponent = event.getComponent();
    RelativePoint point = new RelativePoint(parentComponent, new Point(event.getX()-size.width, event.getY()-size.height));
    popup.show(point);
  }

  public static void showBreakpointEditorBalloon(final Project project,
                                                 @Nullable final Point point, final JComponent component,
                                                 final boolean showAllOptions,
                                                 final XBreakpoint breakpoint) {
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    final XLightBreakpointPropertiesPanel propertiesPanel =
      new XLightBreakpointPropertiesPanel(project, breakpointManager,
                                          breakpoint, showAllOptions);

    final JComponent mainPanel = propertiesPanel.getMainPanel();
    final Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(mainPanel).
      setHideOnAction(false).
      setHideOnClickOutside(true).
      setHideOnKeyOutside(false).
      setCloseButtonEnabled(true).
      setDialogMode(true).
      setFillColor(mainPanel.getBackground()).
      setTitle(breakpoint.getType().getDisplayText(breakpoint)).
      setHideOnFrameResize(false).
      setAnimationCycle(200).createBalloon();
    final XBreakpointListener<XBreakpoint<?>> breakpointListener = new XBreakpointListener<XBreakpoint<?>>() {
      @Override
      public void breakpointAdded(@NotNull XBreakpoint<?> breakpoint1) {
      }

      @Override
      public void breakpointRemoved(@NotNull XBreakpoint<?> breakpoint1) {
        if (breakpoint1.equals(breakpoint)) {
          balloon.hide();
        }
      }

      @Override
      public void breakpointChanged(@NotNull XBreakpoint<?> breakpoint1) {
        if (breakpoint1.equals(breakpoint)) {
          balloon.hide();
        }
      }
    };
    breakpointManager.addBreakpointListener(breakpointListener);
    balloon.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(LightweightWindowEvent event) {
      }

      @Override
      public void onClosed(LightweightWindowEvent event) {
        propertiesPanel.saveProperties();
        breakpointManager.removeBreakpointListener(breakpointListener);
      }
    });

    propertiesPanel.setDelegate(new XLightBreakpointPropertiesPanel.Delegate() {
      @Override
      public void showMoreOptions() {
        balloon.hide();
        showBreakpointEditorBalloon(project, point, component, true, breakpoint);
      }
    });

    propertiesPanel.loadProperties();

    if (point == null) {
      balloon.showInCenterOf(component);
    } else {
      balloon.show(new RelativePoint(component, point), Balloon.Position.atRight);
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        IdeFocusManager.findInstance().requestFocus(mainPanel, true);
      }
    });
  }

  private static class FullValueEvaluationCallbackImpl implements XFullValueEvaluator.XFullValueEvaluationCallback {
    private final AtomicBoolean myObsolete = new AtomicBoolean(false);
    private final JTextArea myTextArea;

    public FullValueEvaluationCallbackImpl(final JTextArea textArea) {
      myTextArea = textArea;
    }

    public void evaluated(@NotNull final String fullValue) {
      evaluated(fullValue, null);
    }

    public void evaluated(@NotNull final String fullValue, @Nullable final Font font) {
      invokeOnEventDispatch(new Runnable() {
        public void run() {
          myTextArea.setText(fullValue);
          if (font != null) {
            myTextArea.setFont(font);
          }
          myTextArea.setCaretPosition(0);
        }
      });
    }

    public void errorOccurred(@NotNull final String errorMessage) {
      invokeOnEventDispatch(new Runnable() {
        public void run() {
          myTextArea.setForeground(XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES.getFgColor());
          myTextArea.setText(errorMessage);
        }
      });
    }

    private void setObsolete() {
      myObsolete.set(true);
    }

    public boolean isObsolete() {
      return myObsolete.get();
    }
  }
}
