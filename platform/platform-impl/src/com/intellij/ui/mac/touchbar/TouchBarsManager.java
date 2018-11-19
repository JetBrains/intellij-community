// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.mac.TouchbarDataKeys;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.Predicate;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.util.*;
import java.util.List;

public class TouchBarsManager {
  private static final Logger LOG = Logger.getInstance(TouchBarsManager.class);
  private static final StackTouchBars ourStack = new StackTouchBars();

  private static final Map<Project, ProjectData> ourProjectData = new HashMap<>(); // NOTE: probably it is better to use api of UserDataHolder
  private static final Map<Container, BarContainer> ourTemporaryBars = new HashMap<>();

  public static void onApplicationInitialized() {
    if (!isTouchBarAvailable())
      return;

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        // System.out.println("opened project " + project + ", set default touchbar");

        final ProjectData pd = new ProjectData(project);
        synchronized (ourProjectData) {
          final ProjectData prev = ourProjectData.put(project, pd);
          if (prev != null) {
            LOG.error("previous project data wasn't removed: " + project);
            prev.releaseAll();
          }
        }

        StartupManager.getInstance(project).registerPostStartupActivity(() -> pd.get(BarType.DEFAULT).show());

        project.getMessageBus().connect().subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
          @Override
          public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
            ApplicationManager.getApplication().invokeLater(TouchBarsManager::_updateCurrentTouchbar);
          }
          @Override
          public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
            // TODO: probably, need to remove debugger-panel from stack completely
            final String twid = env.getExecutor().getToolWindowId();
            ourStack.pop(topContainer -> {
              if (topContainer.getType() != BarType.DEBUGGER)
                return false;

              if (!ToolWindowId.DEBUG.equals(twid) && !ToolWindowId.RUN_DASHBOARD.equals(twid))
                return false;

              // System.out.println("processTerminated, dbgSessionsCount=" + pd.getDbgSessions());
              return !_hasAnyActiveSession(project, handler) || pd.getDbgSessions() <= 0;
            });
            ApplicationManager.getApplication().invokeLater(TouchBarsManager::_updateCurrentTouchbar);
          }
        });
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        // System.out.println("closed project: " + project);

        final ProjectData pd;
        synchronized (ourProjectData) {
          pd = ourProjectData.remove(project);
          if (pd == null) {
            LOG.error("project data already was removed: " + project);
            return;
          }

          ourStack.removeAll(pd.getAllContainers());
          pd.releaseAll();
        }
      }
    });

    _initExecutorsGroup();
  }

  public static boolean isTouchBarAvailable() { return NST.isAvailable(); }

  public static void reloadAll() {
    if (!isTouchBarAvailable())
      return;

    synchronized (ourProjectData) {
      ourProjectData.forEach((p, pd)->pd.reloadAll());
    }
    ourStack.setTouchBarFromTopContainer();
  }

  public static void onInputEvent(InputEvent e) {
    if (!isTouchBarAvailable())
      return;

    // NOTE: skip wheel-events, because scrolling by touchpad produces mouse-wheel events with pressed modifier, expamle:
    // MouseWheelEvent[MOUSE_WHEEL,(890,571),absolute(0,0),button=0,modifiers=⇧,extModifiers=⇧,clickCount=0,scrollType=WHEEL_UNIT_SCROLL,scrollAmount=1,wheelRotation=0,preciseWheelRotation=0.1] on frame0
    if (e instanceof MouseWheelEvent)
      return;

    ourStack.updateKeyMask(e.getModifiersEx() & ProjectData.getUsedKeyMask());
  }

  public static void onFocusEvent(AWTEvent e) {
    if (!isTouchBarAvailable())
      return;

    if (!(e.getSource() instanceof Container))
      return;

    final Container src = (Container)e.getSource();

    // NOTE: WindowEvent.WINDOW_GAINED_FOCUS can be fired when frame focused
    if (e.getID() == FocusEvent.FOCUS_GAINED) {
      if (_hasPopup()) {
        // System.out.println("skip focus event processing because popup exists: " + e);
        return;
      }

      if (_hasNonModalDialog()) {
        final BarContainer barForParent = _findByParentComponent(src, ourTemporaryBars.values(), null);
        if (barForParent != null) {
          // StackTouchBars.changeReason = "non-modal dialog gained focus";
          barForParent.show();
          return;
        }
      }

      synchronized (ourProjectData) {
        for (ProjectData pd: ourProjectData.values()) {
          if (pd.isDisposed())
            continue;

          if (pd.checkToolWindowContents(src)) {
            // System.out.println("tool window gained focus: " + e);
            return;
          }

          final ProjectData.EditorData ed = pd.findEditorDataByComponent(src);
          if (ed != null && ed.containerSearch != null) {
            // System.out.println("editor-component gained focus: " + e);
            // StackTouchBars.changeReason = "editor-search gained focus";
            ourStack.showContainer(ed.containerSearch);
            return;
          }

          final BarContainer twbc = pd.findDebugToolWindowByComponent(src);
          if (twbc != null) {
            // System.out.println("debugger component gained focus: " + e);
            // StackTouchBars.changeReason = "tool-window gained focus";
            ourStack.showContainer(twbc);
            return;
          }
        }
      }
    } else if (e.getID() == FocusEvent.FOCUS_LOST) {
      final BarContainer nonModalDialogParent = _findByParentComponent(src, ourTemporaryBars.values(), bc -> bc.isNonModalDialog());
      if (nonModalDialogParent != null) {
        // System.out.println("non-modal dialog window '" + nonModalDialogParent.getParentComponent() + "' lost focus: " + e);
        // StackTouchBars.changeReason = "non-modal dialog lost focus";
        nonModalDialogParent.hide();
        return;
      }

      synchronized (ourProjectData) {
        for (ProjectData pd: ourProjectData.values()) {
          if (pd.isDisposed())
            continue;

          final ProjectData.EditorData ed = pd.findEditorDataByComponent(src);
          if (ed != null && ed.containerSearch != null) {
            // System.out.println("editor-component lost focus: " + e);
            // StackTouchBars.changeReason = "editor-component lost focus";
            ourStack.removeContainer(ed.containerSearch);
            return;
          }
        }
      }
    }
  }

  public static void registerEditor(@NotNull Editor editor) {
    if (!isTouchBarAvailable())
      return;

    final Project proj = editor.getProject();
    if (proj == null || proj.isDisposed())
      return;

    final ProjectData pd;
    synchronized (ourProjectData) {
      pd = ourProjectData.get(proj);
      if (pd == null) {
        // System.out.println("can't find project data to register editor: " + editor + ", project: " + proj);
        return;
      }

      pd.registerEditor(editor);
    }

    if (editor instanceof EditorEx)
      ((EditorEx)editor).addFocusListener(new FocusChangeListener() {
      @Override
      public void focusGained(@NotNull Editor editor) {
        // System.out.println("reset optional-context of default because editor window gained focus: " + editor);
        pd.get(BarType.DEFAULT).setOptionalContextVisible(null);

        final boolean hasDebugSession = pd.getDbgSessions() > 0;
        if (!hasDebugSession) {
          // System.out.println("elevate default because editor window gained focus: " + editor);
          // StackTouchBars.changeReason = "elevate default because editor gained focus";
          ourStack.elevateContainer(pd.get(BarType.DEFAULT));
        }
      }
      @Override
      public void focusLost(@NotNull Editor editor) {}
    });
  }

  public static void releaseEditor(@NotNull Editor editor) {
    if (!isTouchBarAvailable())
      return;

    final Project proj = editor.getProject();
    if (proj == null)
      return;
    synchronized (ourProjectData) {
      final ProjectData pd = ourProjectData.get(proj);
      if (pd == null)
        return;

      pd.removeEditor(editor);
    }
  }

  public static void onUpdateEditorHeader(@NotNull Editor editor, JComponent header) {
    if (!isTouchBarAvailable())
      return;

    final Project proj = editor.getProject();
    if (proj == null)
      return;

    synchronized (ourProjectData) {
      final ProjectData pd = ourProjectData.get(proj);
      if (pd == null) {
        LOG.error("can't find project data to update header of editor: " + editor + ", project: " + proj);
        return;
      }

      final ProjectData.EditorData ed = pd.getEditorData(editor);
      if (ed == null) {
        LOG.error("can't find editor-data to update header of editor: " + editor + ", project: " + proj);
        return;
      }

      // System.out.printf("onUpdateEditorHeader: editor='%s', header='%s'\n", editor, header);

      final ActionGroup actions = header instanceof DataProvider ? TouchbarDataKeys.ACTIONS_KEY.getData((DataProvider)header) : null;
      if (header == null) {
        // System.out.println("set null header");
        ed.editorHeader = null;
        if (ed.containerSearch != null)
          ourStack.removeContainer(ed.containerSearch);
      } else {
        // System.out.println("set header: " + header);
        // System.out.println("\t\tparent: " + header.getParent());
        ed.editorHeader = header;

        if (ed.containerSearch == null || ed.actionsSearch != actions) {
          if (ed.containerSearch != null) {
            ourStack.removeContainer(ed.containerSearch);
            ed.containerSearch.release();
          }

          if (actions != null) {
            ed.containerSearch = new BarContainer(BarType.EDITOR_SEARCH, TouchBar.buildFromGroup("editor_search_" + header, actions, true, true), null, header);
            ourStack.showContainer(ed.containerSearch);
          }
        }
      }
    }
  }

  public static @Nullable Disposable showPopupBar(@NotNull JBPopup popup, @NotNull JComponent popupComponent) {
    if (!isTouchBarAvailable())
      return null;

    if (!(popup instanceof ListPopupImpl))
      return null;

    @NotNull ListPopupImpl listPopup = (ListPopupImpl)popup;
    final TouchBar tb = BuildUtils.createScrubberBarFromPopup(listPopup);
    BarContainer container = new BarContainer(BarType.POPUP, tb, null, popupComponent);
    ourTemporaryBars.put(popupComponent, container);
    ourStack.showContainer(container);

    return () -> {
      ourStack.removeContainer(container);
      ourTemporaryBars.remove(popupComponent);
      container.release();
    };
  }

  public static @Nullable Disposable showDialogWrapperButtons(@NotNull Container contentPane) {
    if (!isTouchBarAvailable())
      return null;

    final ModalityState ms = Utils.getCurrentModalityState();
    final BarType btype = ModalityState.NON_MODAL.equals(ms) ? BarType.DIALOG : BarType.MODAL_DIALOG;
    BarContainer bc = null;
    TouchBar tb = null;

    final Map<TouchbarDataKeys.DlgButtonDesc, JButton> jbuttons = new HashMap<>();
    final Map<Component, ActionGroup> actions = new HashMap<>();
    _findAllTouchbarProviders(actions, jbuttons, contentPane);

    if (jbuttons.isEmpty() && actions.isEmpty())
      return null;

    boolean replaceEsc = false;
    boolean emulateEsc = false;
    if (!actions.isEmpty()) {
      final ActionGroup ag = actions.values().iterator().next();
      final TouchbarDataKeys.ActionDesc groupDesc = ag.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
      replaceEsc = groupDesc == null || groupDesc.isReplaceEsc();
      emulateEsc = true;
    }
    tb = new TouchBar("dialog_buttons", replaceEsc, false, emulateEsc);
    BuildUtils.addDialogButtons(tb, jbuttons, actions);
    bc = new BarContainer(btype, tb, null, contentPane);

    ourTemporaryBars.put(contentPane, bc);
    ourStack.showContainer(bc);

    final BarContainer fbc = bc;
    return ()->{
      ourTemporaryBars.remove(contentPane);
      ourStack.removeContainer(fbc);
      fbc.release();
    };
  }

  public static void showStopRunningBar(List<? extends Pair<RunContentDescriptor, Runnable>> stoppableDescriptors) {
    final TouchBar tb = BuildUtils.createStopRunningBar(stoppableDescriptors);
    BarContainer container = new BarContainer(BarType.DIALOG, tb, null, null);
    container.setOnHideCallback(() -> { container.release(); });
    ourStack.showContainer(container);
  }

  static void showContainer(@NotNull BarContainer container) { ourStack.showContainer(container); }
  static void hideContainer(@NotNull BarContainer container) { ourStack.removeContainer(container); }

  private static boolean _hasAnyActiveSession(Project proj, ProcessHandler handler/*already terminated*/) {
    final ProcessHandler[] processes = ExecutionManager.getInstance(proj).getRunningProcesses();
    return Arrays.stream(processes).anyMatch(h -> h != null && h != handler && (!h.isProcessTerminated() && !h.isProcessTerminating()));
  }

  private static boolean _hasPopup() { return ourTemporaryBars.values().stream().anyMatch(bc -> bc.isPopup()); }
  private static boolean _hasNonModalDialog() { return ourTemporaryBars.values().stream().anyMatch(bc -> bc.isNonModalDialog()); }

  private static BarContainer _findByParentComponent(Container child, Collection<? extends BarContainer> candidates, Predicate<? super BarContainer> filter) {
    for (BarContainer bc: candidates) {
      if (filter != null && !filter.apply(bc))
        continue;
      if (bc.getParentComponent() == null)
        continue;
      if (SwingUtilities.isDescendingFrom(child, bc.getParentComponent()))
        return bc;
    }
    return null;
  }

  private static void _findAllTouchbarProviders(@NotNull Map<Component, ActionGroup> out, @NotNull Map<TouchbarDataKeys.DlgButtonDesc, JButton> out2, @NotNull Container root) {
    final JBIterable<Component> iter = UIUtil.uiTraverser(root).expandAndFilter(c -> c.isVisible()).traverse();
    for (Component component : iter) {
      if (component instanceof JButton) {
        final TouchbarDataKeys.DlgButtonDesc desc = UIUtil.getClientProperty(component, TouchbarDataKeys.DIALOG_BUTTON_DESCRIPTOR_KEY);
        if (desc != null)
          out2.put(desc, (JButton)component);
      }

      DataProvider dp = null;
      if (component instanceof DataProvider)
        dp = (DataProvider)component;
      else if (component instanceof JComponent)
        dp = DataManager.getDataProvider((JComponent)component);

      if (dp != null) {
        final ActionGroup actions = TouchbarDataKeys.ACTIONS_KEY.getData(dp);
        if (actions != null)
          out.put(component, actions);
      }
    }
  }

  private static void _updateCurrentTouchbar() {
    final TouchBar top = ourStack.getTopTouchBar();
    if (top != null)
      top.updateActionItems();
  }

  private static final String RUNNERS_GROUP_TOUCHBAR = "RunnerActionsTouchbar";

  private static void _initExecutorsGroup() {
    final ActionManager am = ActionManager.getInstance();
    final AnAction runButtons = am.getAction(RUNNERS_GROUP_TOUCHBAR);
    if (runButtons == null) {
      // System.out.println("ERROR: RunnersGroup for touchbar is unregistered");
      return;
    }
    if (!(runButtons instanceof ActionGroup)) {
      // System.out.println("ERROR: RunnersGroup for touchbar isn't a group");
      return;
    }
    final ActionGroup g = (ActionGroup)runButtons;
    for (Executor exec: ExecutorRegistry.getInstance().getRegisteredExecutors()) {
      if (exec != null && (exec.getId().equals(ToolWindowId.RUN) || exec.getId().equals(ToolWindowId.DEBUG))) {
        AnAction action = am.getAction(exec.getId());
        ((DefaultActionGroup)g).add(action);
      }
    }
  }
}
