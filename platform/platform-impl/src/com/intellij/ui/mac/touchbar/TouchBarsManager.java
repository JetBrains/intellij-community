// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class TouchBarsManager {
  private final static boolean IS_LOGGING_ENABLED = false;
  private static final Logger LOG = Logger.getInstance(TouchBarsManager.class);
  private static final ArrayDeque<BarContainer> ourTouchBarStack = new ArrayDeque<>();
  private static TouchBar ourCurrentBar;
  private static long ourCurrentKeyMask;

  public static void attachEditorBar(EditorEx editor) {
    if (!isTouchBarAvailable())
      return;

    final Project proj = editor.getProject();
    if (proj == null)
      return;

    editor.addFocusListener(new FocusChangeListener() {
      private BarContainer myEditorBar = ProjectBarsStorage.instance(proj).getBarContainer(ProjectBarsStorage.EDITOR);
      @Override
      public void focusGained(Editor editor) {
        if (!hasTemporary())
          showTouchBar(myEditorBar);
      }
      @Override
      public void focusLost(Editor editor) {
        if (!hasTemporary())
          closeTouchBar(myEditorBar);
      }
    });
  }

  public static void attachPopupBar(@NotNull ListPopupImpl listPopup) {
    if (!isTouchBarAvailable())
      return;

    listPopup.addPopupListener(new JBPopupListener() {
        private TouchBar myPopupBar = _createScrubberBarFromPopup(listPopup);
        @Override
        public void beforeShown(LightweightWindowEvent event) {
          showTempTouchBar(myPopupBar);
        }
        @Override
        public void onClosed(LightweightWindowEvent event) {
          closeTempTouchBar(myPopupBar);
          myPopupBar = null;
        }
      }
    );
  }

  public static TouchBar showTempButtonsBar(List<JButton> jbuttons, Project project) {
    final TouchBar tb = _createButtonsBar(jbuttons, project);
    showTempTouchBar(tb);
    return tb;
  }

  public static void initialize() {
    if (!isTouchBarAvailable())
      return;

    final ID app = Foundation.invoke("NSApplication", "sharedApplication");
    Foundation.invoke(app, "setAutomaticCustomizeTouchBarMenuItemEnabled:", true);

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        trace("opened project %s, set general touchbar", project);
        showTouchBar(ProjectBarsStorage.instance(project).getBarContainer(ProjectBarsStorage.GENERAL));

        final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(project);
        twm.addToolWindowManagerListener(new ToolWindowManagerListener() {
          private BarContainer myDebuggerBar = ProjectBarsStorage.instance(project).getBarContainer(ProjectBarsStorage.DEBUGGER);

          @Override
          public void toolWindowRegistered(@NotNull String id) {}
          @Override
          public void stateChanged() {
            final String activeId = twm.getActiveToolWindowId();
            if (activeId != null && activeId.equals("Debug"))
              showTouchBar(myDebuggerBar);
            else
              closeTouchBar(myDebuggerBar);
          }
        });
      }
      @Override
      public void projectClosed(Project project) {
        trace("closed project %s, hide touchbar", project);
        closeTouchBar(ProjectBarsStorage.instance(project).getBarContainer(ProjectBarsStorage.GENERAL));
        ProjectBarsStorage.instance(project).releaseAll();
      }
    });
  }

  public static boolean isTouchBarAvailable() { return NST.isAvailable(); }

  public static void onKeyEvent(KeyEvent e) {
    if (!isTouchBarAvailable())
      return;

    if (ourCurrentKeyMask != e.getModifiersEx()) {
//      LOG.debug("change current mask: 0x%X -> 0x%X\n", ourCurrentKeyMask, e.getModifiersEx());
      ourCurrentKeyMask = e.getModifiersEx();
      _setBarContainer(ourTouchBarStack.peek());
    }
  }

  synchronized public static boolean hasTemporary() {
    return ourTouchBarStack.stream().anyMatch((bc)->bc.isTemporary());
  }

  synchronized public static void showTempTouchBar(TouchBar tb) {
    if (tb == null)
      return;

    tb.selectVisibleItemsToShow();
    BarContainer container = new TempBarContainer(tb);
    showTouchBar(container);
  }

  synchronized public static void closeTempTouchBar(TouchBar tb) {
    if (tb == null)
      return;

    tb.release();

    if (ourTouchBarStack.isEmpty())
      return;

    BarContainer top = ourTouchBarStack.peek();
    if (top.get() == tb) {
      ourTouchBarStack.pop();
      _setBarContainer(ourTouchBarStack.peek());
    } else
      ourTouchBarStack.removeIf(bc -> bc.isTemporary() && bc.get() == tb);
  }

  synchronized public static void showTouchBar(@NotNull BarContainer bar) {
    final BarContainer top = ourTouchBarStack.peek();
    if (top == bar)
      return;

    ourTouchBarStack.remove(bar);
    ourTouchBarStack.push(bar);
    _setTouchBar(bar.get());
  }

  synchronized public static void closeTouchBar(@NotNull BarContainer tb) {
    if (ourTouchBarStack.isEmpty())
      return;

    BarContainer top = ourTouchBarStack.peek();
    if (top == tb) {
      ourTouchBarStack.pop();
      _setBarContainer(ourTouchBarStack.peek());
    } else {
      ourTouchBarStack.remove(tb);
    }
  }

  synchronized private static void _setBarContainer(BarContainer barContainer) {
    if (barContainer == null) {
      _setTouchBar(null);
      return;
    }

    if (barContainer instanceof MultiBarContainer)
      ((MultiBarContainer)barContainer).selectBarByKeyMask(ourCurrentKeyMask);

    _setTouchBar(barContainer.get());
  }

  private static void _setTouchBar(TouchBar bar) {
    if (ourCurrentBar == bar)
      return;

    ourCurrentBar = bar;
    NST.setTouchBar(bar);
  }

  private static void trace(String fmt, Object... args) {
    if (IS_LOGGING_ENABLED)
      LOG.trace(String.format(fmt, args));
  }

  private static TouchBar _createButtonsBar(List<JButton> jbuttons, Project project) {
    try (NSAutoreleaseLock lock = new NSAutoreleaseLock()) {
      TouchBarActionBase result = new TouchBarActionBase("dialog_buttons", project);
      final ModalityState ms = LaterInvocator.getCurrentModalityState();
      for (JButton jb : jbuttons) {
        if (jb instanceof JBOptionButton) {
          JBOptionButton ob = (JBOptionButton)jb;
          Action[] opts = ob.getOptions();
          DefaultActionGroup ag = new DefaultActionGroup();
          for (Action a : opts) {
            if (a == null)
              continue;
            AnAction anAct = _createAnAction(a, ob);
            if (anAct == null)
              continue;
            ag.add(anAct);
          }

          if (ag.getChildrenCount() > 0)
            result.addActionGroupButtons(ag, ob, ms);
        }

        final NSTLibrary.Action act = () -> ApplicationManager.getApplication().invokeLater(() -> jb.doClick(), ms);
        result.addButton(null, jb.getText(), act);
      }

      return result;
    }
  }

  private static AnAction _createAnAction(@NotNull Action action, JBOptionButton fromButton) {
    final Object anAct = action.getValue(OptionAction.AN_ACTION);
    if (anAct == null) {
      // LOG.warn("null AnAction in action: '" + action + "', use wrapper");
      return new DumbAwareAction() {
        {
          setEnabledInModalContext(true);
          final Object name = action.getValue(Action.NAME);
          getTemplatePresentation().setText(name != null && name instanceof String ? (String)name : "");
        }
        @Override
        public void actionPerformed(AnActionEvent e) { action.actionPerformed(new ActionEvent(fromButton, ActionEvent.ACTION_PERFORMED, null)); }
        @Override
        public void update(AnActionEvent e) { e.getPresentation().setEnabled(action.isEnabled()); }
      };
    }
    if (!(anAct instanceof AnAction)) {
      // LOG.warn("unknown type of awt.Action's property: " + anAct.getClass().toString());
      return null;
    }
    return (AnAction)anAct;
  }

  private static TouchBar _createScrubberBarFromPopup(@NotNull ListPopupImpl listPopup) {
    try (NSAutoreleaseLock lock = new NSAutoreleaseLock()) {
      final TouchBar result = new TouchBar("popup_scrubber_bar" + listPopup);

      List<TBItemScrubber.ItemData> items = new ArrayList<>();
      @NotNull ListPopupStep listPopupStep = listPopup.getListStep();
      for (Object obj : listPopupStep.getValues()) {
        final Icon ic = listPopupStep.getIconFor(obj);
        final String txt = listPopupStep.getTextFor(obj);

        final Runnable action = () -> {
          listPopup.getList().setSelectedValue(obj, false);
          listPopup.handleSelect(true);
        };

        items.add(new TBItemScrubber.ItemData(ic, txt, () -> ApplicationManager.getApplication().invokeLater(() -> action.run())));
      }
      final TBItemScrubber scrub = result.addScrubber();
      scrub.setItems(items);

      result.selectVisibleItemsToShow();
      return result;
    }
  }

  private static class TempBarContainer implements BarContainer {
    private @NotNull TouchBar myTouchBar;

    TempBarContainer(@NotNull TouchBar tb) { myTouchBar = tb; }
    @Override
    public TouchBar get() { return myTouchBar; }
    @Override
    public void release() { myTouchBar.release(); }
    @Override
    public boolean isTemporary() { return true; }
  }
}
