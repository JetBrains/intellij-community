/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Consumer;
import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointAdapter;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory;
import com.intellij.xdebugger.impl.breakpoints.ui.XLightBreakpointPropertiesPanel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public class DebuggerUIUtil {
  @NonNls public static final String FULL_VALUE_POPUP_DIMENSION_KEY = "XDebugger.FullValuePopup";

  private DebuggerUIUtil() {
  }

  public static void enableEditorOnCheck(final JCheckBox checkbox, final JComponent textfield) {
    checkbox.addActionListener(e -> {
      boolean selected = checkbox.isSelected();
      textfield.setEnabled(selected);
    });
    textfield.setEnabled(checkbox.isSelected());
  }

  public static void focusEditorOnCheck(final JCheckBox checkbox, final JComponent component) {
    final Runnable runnable = () -> component.requestFocus();
    checkbox.addActionListener(e -> {
      if (checkbox.isSelected()) {
        SwingUtilities.invokeLater(runnable);
      }
    });
  }

  public static void invokeLater(Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable);
  }

  @Deprecated
  public static RelativePoint calcPopupLocation(@NotNull Editor editor, final int line) {
    Point p = editor.logicalPositionToXY(new LogicalPosition(line + 1, 0));

    final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    if (!visibleArea.contains(p)) {
      p = new Point((visibleArea.x + visibleArea.width) / 2, (visibleArea.y + visibleArea.height) / 2);
    }
    return new RelativePoint(editor.getContentComponent(), p);
  }

  @Nullable
  public static RelativePoint getPositionForPopup(@NotNull Editor editor, int line) {
    Point p = editor.logicalPositionToXY(new LogicalPosition(line + 1, 0));
    return editor.getScrollingModel().getVisibleArea().contains(p) ? new RelativePoint(editor.getContentComponent(), p) : null;
  }

  public static void showPopupForEditorLine(@NotNull JBPopup popup, @NotNull Editor editor, int line) {
    RelativePoint point = getPositionForPopup(editor, line);
    if (point != null) {
      popup.show(point);
    }
    else {
      Project project = editor.getProject();
      if (project != null) {
        popup.showCenteredInCurrentWindow(project);
      }
      else {
        popup.showInFocusCenter();
      }
    }
  }

  public static void showValuePopup(@NotNull XFullValueEvaluator evaluator, @NotNull MouseEvent event, @NotNull Project project, @Nullable Editor editor) {
    EditorTextField textArea = new TextViewer("Evaluating...", project);
    textArea.setBackground(HintUtil.INFORMATION_COLOR);

    final FullValueEvaluationCallbackImpl callback = new FullValueEvaluationCallbackImpl(textArea);
    evaluator.startEvaluation(callback);

    Dimension size = DimensionService.getInstance().getSize(FULL_VALUE_POPUP_DIMENSION_KEY, project);
    if (size == null) {
      Dimension frameSize = WindowManager.getInstance().getFrame(project).getSize();
      size = new Dimension(frameSize.width / 2, frameSize.height / 2);
    }

    textArea.setPreferredSize(size);

    JBPopup popup = createValuePopup(project, textArea, callback);
    if (editor == null) {
      Rectangle bounds = new Rectangle(event.getLocationOnScreen(), size);
      ScreenUtil.fitToScreenVertical(bounds, 5, 5, true);
      if (size.width != bounds.width || size.height != bounds.height) {
        size = bounds.getSize();
        textArea.setPreferredSize(size);
      }
      popup.showInScreenCoordinates(event.getComponent(), bounds.getLocation());
    }
    else {
      popup.showInBestPositionFor(editor);
    }
  }

  public static JBPopup createValuePopup(Project project,
                                          JComponent component,
                                          @Nullable final FullValueEvaluationCallbackImpl callback) {
    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null);
    builder.setResizable(true)
      .setMovable(true)
        .setDimensionServiceKey(project, FULL_VALUE_POPUP_DIMENSION_KEY, false)
        .setRequestFocus(false);
      if (callback != null) {
        builder.setCancelCallback(() -> {
          callback.setObsolete();
          return true;
        });
      }
    return builder.createPopup();
  }

  public static void showXBreakpointEditorBalloon(final Project project,
                                                  @Nullable final Point point,
                                                  final JComponent component,
                                                  final boolean showAllOptions,
                                                  final XBreakpoint breakpoint) {
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    final XLightBreakpointPropertiesPanel propertiesPanel =
      new XLightBreakpointPropertiesPanel(project, breakpointManager, (XBreakpointBase)breakpoint,
                                                                    showAllOptions);

    final Ref<Balloon> balloonRef = Ref.create(null);
    final Ref<Boolean> isLoading = Ref.create(Boolean.FALSE);
    final Ref<Boolean> moreOptionsRequested = Ref.create(Boolean.FALSE);

    propertiesPanel.setDelegate(() -> {
      if (!isLoading.get()) {
        propertiesPanel.saveProperties();
      }
      if (!balloonRef.isNull()) {
        balloonRef.get().hide();
      }
      showXBreakpointEditorBalloon(project, point, component, true, breakpoint);
      moreOptionsRequested.set(true);
    });

    isLoading.set(Boolean.TRUE);
    propertiesPanel.loadProperties();
    isLoading.set(Boolean.FALSE);

    if (moreOptionsRequested.get()) {
      return;
    }

    Runnable showMoreOptions = () -> {
      propertiesPanel.saveProperties();
      propertiesPanel.dispose();
      BreakpointsDialogFactory.getInstance(project).showDialog(breakpoint);
    };

    final JComponent mainPanel = propertiesPanel.getMainPanel();
    final Balloon balloon = showBreakpointEditor(project, mainPanel, point, component, showMoreOptions, breakpoint);
    balloonRef.set(balloon);

    final XBreakpointListener<XBreakpoint<?>> breakpointListener = new XBreakpointAdapter<XBreakpoint<?>>() {
      @Override
      public void breakpointRemoved(@NotNull XBreakpoint<?> removedBreakpoint) {
        if (removedBreakpoint.equals(breakpoint)) {
          balloon.hide();
        }
      }
    };

    balloon.addListener(new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        propertiesPanel.saveProperties();
        propertiesPanel.dispose();
        breakpointManager.removeBreakpointListener(breakpointListener);
      }
    });

    breakpointManager.addBreakpointListener(breakpointListener);
    ApplicationManager.getApplication().invokeLater(() -> IdeFocusManager.findInstance().requestFocus(mainPanel, true));
  }

  public static Balloon showBreakpointEditor(Project project, final JComponent mainPanel,
                                             final Point whereToShow,
                                             final JComponent component,
                                             @Nullable final Runnable showMoreOptions, Object breakpoint) {
    final BreakpointEditor editor = new BreakpointEditor();
    editor.setPropertiesPanel(mainPanel);
    editor.setShowMoreOptionsLink(true);

    final JPanel panel = editor.getMainPanel();
    final Balloon balloon = JBPopupFactory.getInstance()
      .createDialogBalloonBuilder(panel, null)
      .setHideOnClickOutside(true)
      .setCloseButtonEnabled(false)
      .setAnimationCycle(0)
      .setBlockClicksThroughBalloon(true)
      .createBalloon();


    editor.setDelegate(new BreakpointEditor.Delegate() {
      @Override
      public void done() {
        balloon.hide();
      }

      @Override
      public void more() {
        assert showMoreOptions != null;
        balloon.hide();
        showMoreOptions.run();
      }
    });

    final ComponentAdapter moveListener = new ComponentAdapter() {
      @Override
      public void componentMoved(ComponentEvent e) {
        balloon.hide();
      }
    };
    component.addComponentListener(moveListener);
    Disposer.register(balloon, () -> component.removeComponentListener(moveListener));

    if (whereToShow == null) {
      balloon.showInCenterOf(component);
    }
    else {
      //todo[kb] modify and move to BalloonImpl?
      final Window window = SwingUtilities.windowForComponent(component);
      final RelativePoint p = new RelativePoint(component, whereToShow);
      if (window != null) {
        final RelativePoint point = new RelativePoint(window, new Point(0, 0));
        if (p.getScreenPoint().getX() - point.getScreenPoint().getX() < 40) { // triangle + offsets is ~40px
          p.getPoint().x += 40;
        }
      }
      balloon.show(p, Balloon.Position.below);
    }

    BreakpointsDialogFactory.getInstance(project).setBalloonToHide(balloon, breakpoint);

    return balloon;
  }

  @NotNull
  public static EditorColorsScheme getColorScheme() {
    return EditorColorsUtil.getGlobalOrDefaultColorScheme();
  }

  @NotNull
  public static EditorColorsScheme getColorScheme(@Nullable JComponent component) {
    return EditorColorsUtil.getColorSchemeForComponent(component);
  }

  private static class FullValueEvaluationCallbackImpl implements XFullValueEvaluator.XFullValueEvaluationCallback {
    private final AtomicBoolean myObsolete = new AtomicBoolean(false);
    private final EditorTextField myTextArea;

    public FullValueEvaluationCallbackImpl(final EditorTextField textArea) {
      myTextArea = textArea;
    }

    @Override
    public void evaluated(@NotNull final String fullValue) {
      evaluated(fullValue, null);
    }

    @Override
    public void evaluated(@NotNull final String fullValue, @Nullable final Font font) {
      AppUIUtil.invokeOnEdt(() -> {
        myTextArea.setText(fullValue);
        if (font != null) {
          myTextArea.setFont(font);
        }
      });
    }

    @Override
    public void errorOccurred(@NotNull final String errorMessage) {
      AppUIUtil.invokeOnEdt(() -> {
        myTextArea.setForeground(XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES.getFgColor());
        myTextArea.setText(errorMessage);
      });
    }

    private void setObsolete() {
      myObsolete.set(true);
    }

    @Override
    public boolean isObsolete() {
      return myObsolete.get();
    }
  }

  @Nullable
  public static String getNodeRawValue(@NotNull XValueNodeImpl valueNode) {
    if (valueNode.getValueContainer() instanceof XValueTextProvider) {
      return ((XValueTextProvider)valueNode.getValueContainer()).getValueText();
    }
    else {
      return valueNode.getRawValue();
    }
  }

  /**
   * Checks if value has evaluation expression ready, or calculation is pending
   */
  public static boolean hasEvaluationExpression(@NotNull XValue value) {
    Promise<XExpression> promise = value.calculateEvaluationExpression();
    if (promise.getState() == Promise.State.PENDING) return true;
    if (promise instanceof Getter) {
      return ((Getter)promise).get() != null;
    }
    return true;
  }

  public static void registerActionOnComponent(String name, JComponent component, Disposable parentDisposable) {
    AnAction action = ActionManager.getInstance().getAction(name);
    action.registerCustomShortcutSet(action.getShortcutSet(), component, parentDisposable);
  }

  public static void registerExtraHandleShortcuts(final ListPopupImpl popup, String... actionNames) {
    for (String name : actionNames) {
      KeyStroke stroke = KeymapUtil.getKeyStroke(ActionManager.getInstance().getAction(name).getShortcutSet());
      if (stroke != null) {
        popup.registerAction("handleSelection " + stroke, stroke, new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            popup.handleSelect(true);
          }
        });
      }
    }
  }

  public static String getSelectionShortcutsAdText(String... actionNames) {
    StringBuilder res = new StringBuilder();
    for (String name : actionNames) {
      KeyStroke stroke = KeymapUtil.getKeyStroke(ActionManager.getInstance().getAction(name).getShortcutSet());
      if (stroke != null) {
        if (res.length() > 0) res.append(", ");
        res.append(KeymapUtil.getKeystrokeText(stroke));
      }
    }
    return XDebuggerBundle.message("ad.extra.selection.shortcut", res.toString());
  }

  public static boolean isObsolete(Object object) {
    return object instanceof Obsolescent && ((Obsolescent)object).isObsolete();
  }

  public static void setTreeNodeValue(XValueNodeImpl valueNode, String text, Consumer<String> errorConsumer) {
    XDebuggerTree tree = valueNode.getTree();
    Project project = tree.getProject();
    XValueModifier modifier = valueNode.getValueContainer().getModifier();
    if (modifier == null) return;
    XDebuggerTreeState treeState = XDebuggerTreeState.saveState(tree);
    valueNode.setValueModificationStarted();
    modifier.setValue(text, new XValueModifier.XModificationCallback() {
      @Override
      public void valueModified() {
        if (isDetachedTree(tree)) {
          AppUIUtil.invokeOnEdt(() -> tree.rebuildAndRestore(treeState));
        }
        XDebuggerUtilImpl.rebuildAllSessionsViews(project);
      }

      @Override
      public void errorOccurred(@NotNull final String errorMessage) {
        AppUIUtil.invokeOnEdt(() -> {
          tree.rebuildAndRestore(treeState);
          errorConsumer.consume(errorMessage);
        });
        XDebuggerUtilImpl.rebuildAllSessionsViews(project);
      }

      boolean isDetachedTree(XDebuggerTree tree) {
        return XDebugSessionTab.TAB_KEY.getData(DataManager.getInstance().getDataContext(tree)) == null;
      }
    });
  }
}
