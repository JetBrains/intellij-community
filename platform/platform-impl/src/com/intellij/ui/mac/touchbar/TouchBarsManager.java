// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.util.*;
import java.util.List;

public class TouchBarsManager {
  private static final Logger LOG = Logger.getInstance(TouchBarsManager.class);
  private static final ArrayDeque<BarContainer> ourTouchBarStack = new ArrayDeque<>();
  private static final TouchBarHolder ourTouchBarHolder = new TouchBarHolder();
  private static long ourCurrentKeyMask;

  private static final Map<Project, ProjectData> ourProjectData = new HashMap<>(); // NOTE: probably it is better to use api of UserDataHolder

  public static void initialize() {
    if (!isTouchBarAvailable())
      return;

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        // System.out.println("opened project " + project + ", set default touchbar");

        final ProjectData pd = _getProjData(project);
        showTouchBar(pd.get(BarType.DEFAULT));

        project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
          @Override
          public void stateChanged() {
            final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(project);
            final String activeId = twm.getActiveToolWindowId();
            if (activeId != null && (activeId.equals(ToolWindowId.DEBUG) || activeId.equals(ToolWindowId.RUN_DASHBOARD))) {
              // System.out.println("stateChanged, dbgSessionsCount=" + pd.getDbgSessions());
              if (pd.getDbgSessions() <= 0)
                return;

              showTouchBar(pd.get(BarType.DEBUGGER));
            }
          }
        });

        project.getMessageBus().connect().subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
          @Override
          public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) { ourTouchBarHolder.updateCurrent(); }
          @Override
          public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
            final TouchBar curr;
            final BarContainer top;
            synchronized (TouchBarsManager.class) {
              top = ourTouchBarStack.peek();
              if (top == null)
                return;

              curr = top.get();
              final boolean isDebugger = top.getType() == BarType.DEBUGGER;
              if (isDebugger) {
                if (executorId.equals(ToolWindowId.DEBUG) || executorId.equals(ToolWindowId.RUN_DASHBOARD)) {
                  // System.out.println("processTerminated, dbgSessionsCount=" + pd.getDbgSessions());
                  final boolean hasDebugSession = _hasAnyActiveSession(project, handler);
                  if (!hasDebugSession || pd.getDbgSessions() <= 0)
                    closeTouchBar(top);
                }
              }
            }

            if (curr instanceof TouchBarActionBase)
              ApplicationManager.getApplication().invokeLater(() -> { ((TouchBarActionBase)curr).updateActionItems(); });
          }
        });
      }
      @Override
      public void projectClosed(Project project) {
        // System.out.println("closed project " + project + ", hide touchbar");
        final ProjectData pd = _getProjData(project);
        closeTouchBar(pd.get(BarType.DEFAULT));
        pd.releaseAll();
        ourProjectData.remove(project);
      }
    });
  }

  public static void reloadAll() {
    if (!isTouchBarAvailable())
      return;

    ourProjectData.forEach((p, pd)->{
      pd.reloadAll();
    });
    _setBarContainer(ourTouchBarStack.peek());
  }

  public static boolean isTouchBarAvailable() { return NST.isAvailable(); }

  synchronized public static void showTouchBar(BarContainer bar) {
    if (bar == null)
      return;

    final BarContainer top = ourTouchBarStack.peek();
    if (top == bar)
      return;

    ourTouchBarStack.remove(bar);
    ourTouchBarStack.push(bar);
    _setBarContainer(bar);
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

  public static void onInputEvent(InputEvent e) {
    if (!isTouchBarAvailable())
      return;

    // NOTE: skip wheel-events, because scrolling by touchpad produces mouse-wheel events with pressed modifier, expamle:
    // MouseWheelEvent[MOUSE_WHEEL,(890,571),absolute(0,0),button=0,modifiers=⇧,extModifiers=⇧,clickCount=0,scrollType=WHEEL_UNIT_SCROLL,scrollAmount=1,wheelRotation=0,preciseWheelRotation=0.1] on frame0
    if (e instanceof MouseWheelEvent)
      return;

    if (ourCurrentKeyMask != e.getModifiersEx()) {
//      LOG.debug("change current mask: 0x%X -> 0x%X\n", ourCurrentKeyMask, e.getModifiersEx());
      ourCurrentKeyMask = e.getModifiersEx();
      _setBarContainer(ourTouchBarStack.peek());
    }
  }

  public static void onFocusEvent(AWTEvent e) {
    if (!isTouchBarAvailable())
      return;

    // NOTE: WindowEvent.WINDOW_GAINED_FOCUS can be fired when frame focused
    if (e.getID() == FocusEvent.FOCUS_GAINED) {
      if (!(e.getSource() instanceof Component))
        return;

      ourProjectData.forEach((project, data) -> {
        final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(project);
        if (twm == null)
          return;

        final ToolWindow dtw = twm.getToolWindow(ToolWindowId.DEBUG);
        final ToolWindow rtw = twm.getToolWindow(ToolWindowId.RUN_DASHBOARD);

        final Component compD = dtw != null ? dtw.getComponent() : null;
        final Component compR = rtw != null ? rtw.getComponent() : null;
        if (compD == null && compR == null)
          return;

        if (
          e.getSource() == compD || e.getSource() == compR
          || (compD != null && SwingUtilities.isDescendingFrom((Component)e.getSource(), compD))
          || (compR != null && SwingUtilities.isDescendingFrom((Component)e.getSource(), compR))
        )
          showTouchBar(data.get(BarType.DEBUGGER));
      });
    }
  }

  public static void attachEditorBar(EditorEx editor) {
    if (!isTouchBarAvailable())
      return;

    final Project proj = editor.getProject();
    if (proj == null)
      return;

    editor.addFocusListener(new FocusChangeListener() {
      @Override
      public void focusGained(Editor editor) {
        _elevateTouchBar(_getProjData(proj).get(BarType.DEFAULT));
      }
      @Override
      public void focusLost(Editor editor) {}
    });
  }

  public static void attachPopupBar(@NotNull ListPopupImpl listPopup) {
    if (!isTouchBarAvailable())
      return;

    listPopup.addPopupListener(new JBPopupListener() {
        private TouchBar myPopupBar = _createScrubberBarFromPopup(listPopup);
        @Override
        public void beforeShown(LightweightWindowEvent event) {
          _showTempTouchBar(myPopupBar, BarType.POPUP);
        }
        @Override
        public void onClosed(LightweightWindowEvent event) {
          closeTouchBar(myPopupBar, true);
          myPopupBar = null;
        }
      }
    );
  }

  public static @Nullable Runnable showDlgButtonsBar(List<JButton> jbuttons, Project project) {
    if (!isTouchBarAvailable())
      return null;

    final TouchBar tb = _createButtonsBar(jbuttons, project);
    _showTempTouchBar(tb, BarType.DIALOG);
    return ()->{closeTouchBar(tb, true);};
  }

  synchronized public static void showStopRunningBar(TouchBar tb) {
    _showTempTouchBar(tb, BarType.DIALOG);
  }

  synchronized public static Runnable showMessageDlgBar(@NotNull String[] buttons, @NotNull Runnable[] actions, String defaultButton) {
    if (!isTouchBarAvailable())
      return null;

    // NOTE: buttons are placed from right to left, see SheetController.layoutButtons
    final List<TBItem> groupButtons = new ArrayList<>();
    int defIndex = -1;
    final int len = Math.min(buttons.length, actions.length);
    for (int c = 0; c < len; ++c) {
      final String sb = buttons[c];
      final boolean isDefault = Comparing.equal(sb, defaultButton);
      if (isDefault) {
        defIndex = c;
        continue;
      }
      groupButtons.add(new TBItemButton(
        "message_dlg_bar_group_item_" + c,
        null, DialogWrapper.extractMnemonic(sb).second, NSTLibrary.run2act(actions[c]), -1, 0
      ));
    }
    Collections.reverse(groupButtons);

    if (defIndex >= 0)
      groupButtons.add(new TBItemButton(
        "message_dlg_bar_group_item_default",
        null, DialogWrapper.extractMnemonic(buttons[defIndex]).second, NSTLibrary.run2act(actions[defIndex]), -1, NSTLibrary.BUTTON_FLAG_COLORED
      ));

    final TouchBar tb;
    try (NSAutoreleaseLock lock = new NSAutoreleaseLock()) {
      tb = new TouchBar("message_dlg_bar", false);
      final TBItemGroup gr = tb.addGroup(groupButtons);
      tb.setPrincipal(gr);
    }

    _showTempTouchBar(tb, BarType.DIALOG);
    return ()->{closeTouchBar(tb, true);};
  }

  synchronized private static void _showTempTouchBar(TouchBar tb, BarType type) {
    if (tb == null)
      return;

    tb.selectVisibleItemsToShow();
    BarContainer container = new BarContainer(type, tb, null);
    showTouchBar(container);
  }

  synchronized private static void _elevateTouchBar(BarContainer bar) {
    if (bar == null)
      return;

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
      ourTouchBarHolder.setTouchBar(null);
      return;
    }

    barContainer.selectBarByKeyMask(ourCurrentKeyMask);
    ourTouchBarHolder.setTouchBar(barContainer.get());
  }

  private static class TouchBarHolder {
    private TouchBar myCurrentBar;
    private TouchBar myNextBar;

    synchronized void setTouchBar(TouchBar bar) {
      // the usual event sequence "focus lost -> show underlay bar -> focus gained" produces annoying flicker
      // use slightly deferred update to skip "showing underlay bar"
      myNextBar = bar;
      final Timer timer = new Timer(50, (event)->{
        _setNextTouchBar();
      });
      timer.setRepeats(false);
      timer.start();
    }

    synchronized void updateCurrent() {
      if (myCurrentBar instanceof TouchBarActionBase)
        ((TouchBarActionBase)myCurrentBar).updateActionItems();
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

  private static TouchBar _createButtonsBar(List<JButton> jbuttons, Project project) {
    try (NSAutoreleaseLock lock = new NSAutoreleaseLock()) {
      final TouchBarActionBase result = new TouchBarActionBase("dialog_buttons", project);
      final ModalityState ms = LaterInvocator.getCurrentModalityState();

      // 1. add option buttons (at left)
      for (JButton jb : jbuttons) {
        if (jb instanceof JBOptionButton) {
          final JBOptionButton ob = (JBOptionButton)jb;
          final Action[] opts = ob.getOptions();
          for (Action a : opts) {
            if (a == null)
              continue;
            final AnAction anAct = _createAnAction(a, ob, true);
            if (anAct == null)
              continue;

            final TBItemAnActionButton butt = new TBItemAnActionButton(result.genNewID(a.toString()), anAct, false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY, ms);
            butt.setComponent(ob);
            result.myItems.add(butt);
          }
        }
      }

      // 2. set different priorities for items, otherwise system can hide all items with the same priority (but some of them is able to be placed)
      byte prio = -1;
      for (TBItem item: result.myItems) {
        if (item instanceof TBItemButton)
          ((TBItemButton)item).setPriority(--prio);
      }

      // 3. add main buttons and make principal
      final List<TBItem> groupButtons = new ArrayList<>();
      TBItemAnActionButton def = null;
      for (JButton jb : jbuttons) {
        // TODO: make correct processing for disabled buttons, add them and update state by timer
        // NOTE: can be true: jb.getAction().isEnabled() && !jb.isEnabled()

        final AnAction anAct = _createAnAction(jb.getAction(), jb, false);
        if (anAct == null)
          continue;

        final int index = jbuttons.indexOf(jb);
        final TBItemAnActionButton butt = new TBItemAnActionButton("dialog_buttons_group_item_" + index, anAct, false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY, ms);
        butt.setComponent(jb);

        final boolean isDefault = jb.getAction().getValue(DialogWrapper.DEFAULT_ACTION) != null;
        if (isDefault) {
          def = butt;
          def.myFlags |= NSTLibrary.BUTTON_FLAG_COLORED;
          continue;
        }
        groupButtons.add(butt);
      }

      if (def != null)
        groupButtons.add(def);

      final TBItemGroup gr = result.addGroup(groupButtons);
      result.setPrincipal(gr);

      return result;
    }
  }

  private static AnAction _createAnAction(@NotNull Action action, JButton fromButton, boolean useTextFromAction /*for optional buttons*/) {
    final Object anAct = action.getValue(OptionAction.AN_ACTION);
    if (anAct == null) {
      // LOG.warn("null AnAction in action: '" + action + "', use wrapper");
      return new DumbAwareAction() {
        {
          setEnabledInModalContext(true);
          if (useTextFromAction) {
            final Object name = action.getValue(Action.NAME);
            getTemplatePresentation().setText(name != null && name instanceof String ? (String)name : "");
          }
        }
        @Override
        public void actionPerformed(AnActionEvent e) {
          // also can be used something like: ApplicationManager.getApplication().invokeLater(() -> jb.doClick(), ms)
          action.actionPerformed(new ActionEvent(fromButton, ActionEvent.ACTION_PERFORMED, null));
        }
        @Override
        public void update(AnActionEvent e) {
          e.getPresentation().setEnabled(action.isEnabled());
          if (!useTextFromAction)
            e.getPresentation().setText(DialogWrapper.extractMnemonic(fromButton.getText()).second);
        }
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

  private static boolean _hasAnyActiveSession(Project proj, ProcessHandler handler/*already terminated*/) {
    final ProcessHandler[] processes = ExecutionManager.getInstance(proj).getRunningProcesses();
    return Arrays.stream(processes).anyMatch(h -> h != null && h != handler && (!h.isProcessTerminated() && !h.isProcessTerminating()));
  }

  private static @NotNull ProjectData _getProjData(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ProjectData result = ourProjectData.get(project);
    if (result == null) {
      result = new ProjectData(project);
      ourProjectData.put(project, result);
    }
    return result;
  }
}
