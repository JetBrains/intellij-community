// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;

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
  private final static boolean IS_LOGGING_ENABLED = false;
  private static final Logger LOG = Logger.getInstance(TouchBarsManager.class);
  private static final ArrayDeque<BarContainer> ourTouchBarStack = new ArrayDeque<>();
  private static final TouchBarHolder ourTouchBarHolder = new TouchBarHolder();
  private static long ourCurrentKeyMask;

  private static final Map<Project, ProjectData> ourProjectData = new HashMap<>(); // NOTE: probably it is better to use api of UserDataHolder

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

  public static TouchBar showTempButtonsBar(List<JButton> jbuttons, Project project) {
    final TouchBar tb = _createButtonsBar(jbuttons, project);
    _showTempTouchBar(tb, BarType.DIALOG);
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
        trace("opened project %s, set default touchbar", project);

        final ProjectData pd = _getProjData(project);
        showTouchBar(pd.get(BarType.DEFAULT));

        project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
          @Override
          public void stateChanged() {
            final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(project);
            final String activeId = twm.getActiveToolWindowId();
            if (activeId != null && activeId.equals(ToolWindowId.DEBUG)) {
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
                if (executorId.equals(ToolWindowId.DEBUG)) {
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
        trace("closed project %s, hide touchbar", project);
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

    // NOTE: WindowEvent.WINDOW_GAINED_FOCUS can be fired when frame focuse
    if (e.getID() == FocusEvent.FOCUS_GAINED) {
      ourProjectData.forEach((project, data) -> {
        final ToolWindow dtw = ToolWindowManagerEx.getInstanceEx(project).getToolWindow(ToolWindowId.DEBUG);
        if (dtw == null)
          return;

        final Component comp = dtw.getComponent();
        if (comp == null)
          return;

        if (!(e.getSource() instanceof Component))
          return;

        if (e.getSource() == comp || SwingUtilities.isDescendingFrom((Component)e.getSource(), comp))
          showTouchBar(data.get(BarType.DEBUGGER));
      });
    }
  }


  synchronized public static void showTempTouchBar(TouchBar tb) {
    _showTempTouchBar(tb, BarType.DIALOG);
  }

  synchronized public static Runnable showMessageDlgBar(@NotNull String[] buttons, @NotNull Runnable[] actions, String defaultButton) {
    if (!isTouchBarAvailable())
      return null;

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

  private static void trace(String fmt, Object... args) {
    if (IS_LOGGING_ENABLED)
      LOG.trace(String.format(fmt, args));
  }

  private static TouchBar _createButtonsBar(List<JButton> jbuttons, Project project) {
    try (NSAutoreleaseLock lock = new NSAutoreleaseLock()) {
      TouchBarActionBase result = new TouchBarActionBase("dialog_buttons", project);
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

          if (ag.getChildrenCount() > 0) {
            result.addActionGroupButtons(ag, ms, TBItemAnActionButton.SHOWMODE_TEXT_ONLY, null, item -> {
              if (item instanceof TBItemAnActionButton)
                ((TBItemAnActionButton)item).setComponent(ob);
            });
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
      JButton jdef = null;
      for (JButton jb : jbuttons) {
        // TODO: make correct processing for disabled buttons, add them and update state by timer
        // NOTE: can be true: jb.getAction().isEnabled() && !jb.isEnabled()
        final boolean isDefault = jb.getAction().getValue(DialogWrapper.DEFAULT_ACTION) != null;
        if (isDefault) {
          jdef = jb;
          continue;
        }
        final TBItemButton butt = _jbutton2item(jb, jbuttons.indexOf(jb), ms);
        groupButtons.add(butt);
      }

      if (jdef != null)
        groupButtons.add(_jbutton2item(jdef, jbuttons.indexOf(jdef), ms));

      final TBItemGroup gr = result.addGroup(groupButtons);
      result.setPrincipal(gr);

      return result;
    }
  }

  private static TBItemButton _jbutton2item(JButton jb, int index, ModalityState ms) {
    final NSTLibrary.Action act = () -> ApplicationManager.getApplication().invokeLater(() -> jb.doClick(), ms);
    final boolean isDefault = jb.getAction().getValue(DialogWrapper.DEFAULT_ACTION) != null;
    return new TBItemButton(
      "dialog_buttons_group_item_" + index,
      null, DialogWrapper.extractMnemonic(jb.getText()).second, act, -1, isDefault ? NSTLibrary.BUTTON_FLAG_COLORED : 0
    );
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
