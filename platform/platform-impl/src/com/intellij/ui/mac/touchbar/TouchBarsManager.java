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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class TouchBarsManager {
  private final static boolean IS_LOGGING_ENABLED = false;
  private static final Logger LOG = Logger.getInstance(TouchBarsManager.class);
  private static final ArrayDeque<BarContainer> ourTouchBarStack = new ArrayDeque<>();
  private static final ChangeScheduler ourTouchBarChanger = new ChangeScheduler();
  private static long ourCurrentKeyMask;

  public static void attachEditorBar(EditorEx editor) {
    if (!isTouchBarAvailable())
      return;

    final Project proj = editor.getProject();
    if (proj == null)
      return;

    editor.addFocusListener(new FocusChangeListener() {
      private final BarContainer myEditorBar = ProjectBarsStorage.instance(proj).createBarContainer(ProjectBarsStorage.EDITOR, editor.getContentComponent());

      @Override
      public void focusGained(Editor editor) {
        _elevateTouchBar(myEditorBar);
      }
      @Override
      public void focusLost(Editor editor) {
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
          closeTouchBar(myPopupBar, true);
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
      private BarContainer myGeneralBar;
      @Override
      public void projectOpened(Project project) {
        trace("opened project %s, set general touchbar", project);
        myGeneralBar = ProjectBarsStorage.instance(project).createBarContainer(ProjectBarsStorage.GENERAL, null);
        showTouchBar(myGeneralBar);

        project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
          private BarContainer myDebuggerBar;

          @Override
          public void stateChanged() {
            final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(project);
            final String activeId = twm.getActiveToolWindowId();
            if (activeId != null && activeId.equals("Debug")) {
              // TODO:
              // 1. check whether some debug session is running
              // 2. stateChanged can be skipped sometimes when user clicks debug tool-window, need check by focus events or fix stateChanged-subscription
              if (myDebuggerBar == null) {
                myDebuggerBar = ProjectBarsStorage.instance(project).createBarContainer(ProjectBarsStorage.DEBUGGER,
                                                                                        twm.getToolWindow(activeId).getComponent());
              }
              showTouchBar(myDebuggerBar);
            }
          }
        });
      }
      @Override
      public void projectClosed(Project project) {
        trace("closed project %s, hide touchbar", project);
        closeTouchBar(myGeneralBar);
        ProjectBarsStorage.instance(project).releaseAll();
      }
    });
  }

  public static boolean isTouchBarAvailable() { return NST.isAvailable(); }

  public static void onInputEvent(InputEvent e) {
    if (!isTouchBarAvailable())
      return;

    if (ourCurrentKeyMask != e.getModifiersEx()) {
//      LOG.debug("change current mask: 0x%X -> 0x%X\n", ourCurrentKeyMask, e.getModifiersEx());
      ourCurrentKeyMask = e.getModifiersEx();
      _setBarContainer(ourTouchBarStack.peek());
    }
  }

  synchronized public static void showTempTouchBar(TouchBar tb) {
    if (tb == null)
      return;

    tb.selectVisibleItemsToShow();
    BarContainer container = new TempBarContainer(tb);
    showTouchBar(container);
  }

  synchronized public static void closeTouchBar(TouchBar tb, boolean doRelease) {
    if (tb == null)
      return;

    if (doRelease)
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
    _setBarContainer(bar);
  }

  synchronized private static void _elevateTouchBar(@NotNull BarContainer bar) {
    final BarContainer top = ourTouchBarStack.peek();
    if (top == bar)
      return;

    final boolean preserveTop = top != null && (top.isTemporary() || top.get().isManualClose());
    if (preserveTop) {
      ourTouchBarStack.remove(bar);
      ourTouchBarStack.remove(top);
      ourTouchBarStack.push(bar);
      ourTouchBarStack.push(top);
    } else {
      ourTouchBarStack.remove(bar);
      ourTouchBarStack.push(bar);
      _setBarContainer(bar);
    }
  }

  synchronized public static void closeTouchBar(BarContainer tb) {
    if (tb == null || ourTouchBarStack.isEmpty())
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
      ourTouchBarChanger.updateTouchBar(null);
      return;
    }

    if (barContainer instanceof MultiBarContainer)
      ((MultiBarContainer)barContainer).selectBarByKeyMask(ourCurrentKeyMask);

    ourTouchBarChanger.updateTouchBar(barContainer.get());
  }

  private static class ChangeScheduler {
    private TouchBar myCurrentBar;
    private TouchBar myNextBar;

    synchronized void updateTouchBar(TouchBar bar) {
      // the usual event sequence "focus lost -> show underlay bar -> focus gained" produces annoying flicker
      // use slightly deferred update to skip "showing underlay bar"
      myNextBar = bar;
      final Timer timer = new Timer(50, (event)->{
        _setNextTouchBar();
      });
      timer.setRepeats(false);
      timer.start();
    }

    synchronized private void _setNextTouchBar() {
      if (myCurrentBar == myNextBar) {
        return;
      }

      if (myCurrentBar != null)
        myCurrentBar.onHide();
      myCurrentBar = myNextBar;
      if (myCurrentBar != null)
        myCurrentBar.onBeforeShow();
      NST.setTouchBar(myCurrentBar);
    }
  }

  private static void trace(String fmt, Object... args) {
    if (IS_LOGGING_ENABLED)
      LOG.trace(String.format(fmt, args));
  }

  private static TouchBar _createButtonsBar(List<JButton> jbuttons, Project project) {
    try (NSAutoreleaseLock lock = new NSAutoreleaseLock()) {
      TouchBarActionBase result = new TouchBarActionBase("dialog_buttons", project, null);
      final ModalityState ms = LaterInvocator.getCurrentModalityState();

      // 1. add option buttons (at left)
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
      }

      // 2. add main buttons and make principal
      final List<TBItem> groupButtons = new ArrayList<>();
      for (JButton jb : jbuttons) {
        // TODO: make correct processing for disabled buttons, add them and update state by timer
        // NOTE: can be true: jb.getAction().isEnabled() && !jb.isEnabled()
        final NSTLibrary.Action act = () -> ApplicationManager.getApplication().invokeLater(() -> jb.doClick(), ms);
        final boolean isDefault = jb.getAction().getValue(DialogWrapper.DEFAULT_ACTION) != null;
        final TBItemButton butt = new TBItemButton(
          "dialog_buttons_group_item_" + jbuttons.indexOf(jb),
          null, DialogWrapper.extractMnemonic(jb.getText()).second, act, -1, isDefault ? NSTLibrary.BUTTON_FLAG_COLORED : 0
          );
        groupButtons.add(butt);
      }

      final TBItemGroup gr = result.addGroup(groupButtons);
      result.setPrincipal(gr);

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
      final TouchBar result = new TouchBar("popup_scrubber_bar" + listPopup, false);

      List<TBItemScrubber.ItemData> items = new ArrayList<>();
      @NotNull ListPopupStep listPopupStep = listPopup.getListStep();
      for (Object obj : listPopupStep.getValues()) {
        final Icon ic = listPopupStep.getIconFor(obj);
        String txt = listPopupStep.getTextFor(obj);

        if (listPopupStep.isMnemonicsNavigationEnabled()) {
          final MnemonicNavigationFilter<Object> filter = listPopupStep.getMnemonicNavigationFilter();
          final int pos = filter == null ? -1 : filter.getMnemonicPos(obj);
          if (pos != -1)
            txt = txt.substring(0, pos) + txt.substring(pos + 1);
        }

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

  private static class TempBarContainer extends SingleBarContainer {
    TempBarContainer(@NotNull TouchBar tb) { super(tb); }
    @Override
    public boolean isTemporary() { return true; }
  }
}
