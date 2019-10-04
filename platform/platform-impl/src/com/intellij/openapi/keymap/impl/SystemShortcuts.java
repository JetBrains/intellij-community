// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

public class SystemShortcuts {
  private static final Logger LOG = Logger.getInstance(SystemShortcuts.class);
  private static final @NotNull String ourNotificationGroupId = "System shortcuts conflicts";
  private static final @NotNull String ourUnknownSysAction = "Unknown action";

  private static boolean ourIsNotificationRegistered = false;

  private @NotNull final Map<KeyStroke, AWTKeyStroke> myKeyStroke2SysShortcut = new HashMap<>();
  private @NotNull final MuteConflictsSettings myMutedConflicts = new MuteConflictsSettings();

  private @Nullable Keymap myKeymap;
  private @NotNull Map<AWTKeyStroke, ConflictItem> myKeymapConflicts = new HashMap<>();

  public SystemShortcuts() {
    readSystem();
  }

  public static class ConflictItem {
    final @NotNull String mySysActionDesc;
    final @NotNull KeyStroke mySysKeyStroke;
    final @NotNull String[] myActionIds;

    public ConflictItem(@NotNull KeyStroke sysKeyStroke, @NotNull String sysActionDesc, @NotNull String[] actionIds) {
      mySysKeyStroke = sysKeyStroke;
      mySysActionDesc = sysActionDesc;
      myActionIds = actionIds;
    }

    @NotNull
    public String getSysActionDesc() { return mySysActionDesc; }
    @NotNull
    public KeyStroke getSysKeyStroke() { return mySysKeyStroke; }
    @NotNull
    public String[] getActionIds() { return myActionIds; }

    @Nullable String getUnmutedActionId(@NotNull MuteConflictsSettings settings) {
      for (String actId: myActionIds)
        if (!settings.isMutedAction(actId))
          return actId;
      return null;
    }
  }

  public void updateKeymapConflicts(@Nullable Keymap keymap) {
    myKeymap = keymap;
    myKeymapConflicts.clear();

    if (myKeyStroke2SysShortcut.isEmpty())
      return;

    for (@NotNull KeyStroke sysKS: myKeyStroke2SysShortcut.keySet()) {
      final String[] actIds = computeOnEdt(() -> keymap.getActionIds(sysKS));
      if (actIds == null || actIds.length == 0)
        continue;

      @NotNull AWTKeyStroke shk = myKeyStroke2SysShortcut.get(sysKS);
      myKeymapConflicts.put(shk, new ConflictItem(sysKS, getDescription(shk), actIds));
    }
  }

  @NotNull
  public Collection<ConflictItem> getUnmutedKeymapConflicts() {
    List<ConflictItem> result = new ArrayList<>();
    myKeymapConflicts.forEach((ks, ci) -> {
      if (ci.getUnmutedActionId(myMutedConflicts) != null)
        result.add(ci);
    });
    return result;
  }

  @Nullable
  public Condition<AnAction> createKeymapConflictsActionFilter() {
    if (myKeyStroke2SysShortcut.isEmpty() || myKeymap == null)
      return null;

    final Condition<Shortcut> predicat = sc -> {
      if (sc == null)
        return false;
      for (KeyStroke ks: myKeyStroke2SysShortcut.keySet()) {
        if (sc.startsWith(new KeyboardShortcut(ks, null))) {
          final ConflictItem ci = myKeymapConflicts.get(myKeyStroke2SysShortcut.get(ks));
          if (ci != null && ci.getUnmutedActionId(myMutedConflicts) != null)
            return true;
        }
      }
      return false;
    };
    return ActionsTreeUtil.isActionFiltered(ActionManager.getInstance(), myKeymap, predicat);
  }

  public void checkConflictsAndNotify(@NotNull Keymap keymap) {
    if (myKeyStroke2SysShortcut.isEmpty())
      return;

    updateKeymapConflicts(keymap);

    //System.out.printf("\n========================== found %d conflicts: =========================\n\n", myConflicts.size());

    final List<AWTKeyStroke> keys = new ArrayList<>(myKeymapConflicts.keySet());
    keys.sort((c0, c1) -> {
      if (c0.getKeyChar() != KeyEvent.CHAR_UNDEFINED && c1.getKeyChar() != KeyEvent.CHAR_UNDEFINED)
        return c0.getKeyChar() - c1.getKeyChar();
      if (c0.getKeyChar() != KeyEvent.CHAR_UNDEFINED)
        return -1;
      if (c1.getKeyChar() != KeyEvent.CHAR_UNDEFINED)
        return 1;
      return c0.getKeyCode() - c1.getKeyCode();
    });

    for (AWTKeyStroke shk: keys) {
      final @NotNull ConflictItem conflictItem = myKeymapConflicts.get(shk);
      final @NotNull KeyStroke sysKS = conflictItem.mySysKeyStroke;
      final @Nullable String actionId = conflictItem.getUnmutedActionId(myMutedConflicts);

      if (actionId == null) {
        //System.out.println("Skip muted: " + actionId);
        continue;
      }

      final Shortcut[] actionShortcuts = computeOnEdt(() -> keymap.getShortcuts(actionId));
      if (actionShortcuts == null || actionShortcuts.length == 0) {
        LOG.error(String.format("keymap %s found actions '%s' by keystroke='%s' but can't find shortcuts for action '%s'", keymap, Arrays.toString(conflictItem.myActionIds), sysKS, actionId));
        continue;
      }

      final @Nullable String macOsShortcutAction = getDescription(shk);
      //System.out.println(actionId + " shortcut '" + sysKS + "' "
      //                   + Arrays.toString(actionShortcuts) + " conflicts with macOS shortcut"
      //                   + (macOsShortcutAction == null ? "." : " '" + macOsShortcutAction + "'."));

      KeyboardShortcut conflicted = null;
      for (Shortcut sc: actionShortcuts) {
        if (!(sc instanceof KeyboardShortcut))
          continue;
        final KeyboardShortcut ksc = (KeyboardShortcut)sc;
        if (sysKS.equals(ksc.getFirstKeyStroke()) || sysKS.equals(ksc.getSecondKeyStroke())) {
          conflicted = ksc;
          break;
        }
      }
      if (conflicted == null) {
        LOG.error("can't find conflict shortcut of action " + actionId + ", system-shortcut='" + sysKS + "', action shortcuts: " + Arrays.toString(actionShortcuts));
        continue;
      }
      doNotify(keymap, actionId, sysKS, macOsShortcutAction, conflicted);
      break; // Multiple conflicts notifications will be implemented later
    }
  }

  public @Nullable Map<KeyboardShortcut, String> calculateConflicts(@NotNull Keymap keymap, @NotNull String actionId) {
    if (myKeyStroke2SysShortcut.isEmpty())
      return null;

    Map<KeyboardShortcut, String> result = null;
    final Shortcut[] actionShortcuts = computeOnEdt(() -> keymap.getShortcuts(actionId));
    for (Shortcut sc: actionShortcuts) {
      if (!(sc instanceof KeyboardShortcut)) {
        continue;
      }
      final KeyboardShortcut ksc = (KeyboardShortcut)sc;
      for (@NotNull KeyStroke sks: myKeyStroke2SysShortcut.keySet()) {
        if (ksc.getFirstKeyStroke().equals(sks) || sks.equals(ksc.getSecondKeyStroke())) {
          if (result == null) result = new HashMap<>();
          result.put(ksc, getDescription(myKeyStroke2SysShortcut.get(sks)));
        }
      }
    }
    return result;
  }

  private static <T> T computeOnEdt(Supplier<T> supplier) {
    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      return supplier.get();
    }

    final Ref<T> result = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      result.set(supplier.get());
    });
    return result.get();
  }

  public @Nullable Map<KeyStroke, String> createKeystroke2SysShortcutMap() {
    if (myKeyStroke2SysShortcut.isEmpty())
      return null;

    final Map<KeyStroke, String> result = new HashMap<>();
    myKeyStroke2SysShortcut.forEach((ks, sysks) -> result.put(ks, getDescription(sysks)));
    return result;
  }

  private int getUnmutedConflictsCount() {
    if (myKeymapConflicts.isEmpty())
      return 0;
    int result = 0;
    for (ConflictItem ci: myKeymapConflicts.values())
      if (ci.getUnmutedActionId(myMutedConflicts) != null)
        result++;
    return result;
  }

  private void doNotify(@NotNull Keymap keymap, @NotNull String actionId, @NotNull KeyStroke sysKS, @Nullable String macOsShortcutAction, @NotNull KeyboardShortcut conflicted) {
    if (!ourIsNotificationRegistered) {
      ourIsNotificationRegistered = true;
      NotificationsConfiguration.getNotificationsConfiguration().register(
        ourNotificationGroupId,
        NotificationDisplayType.STICKY_BALLOON,
        true);
    }

    final String message = actionId + " shortcut conflicts with macOS shortcut" + (macOsShortcutAction == null ? "" : " '" + macOsShortcutAction + "'") + ".";
    final Notification notification = new Notification(ourNotificationGroupId, "Shortcuts conflicts", message, NotificationType.WARNING, null);

    final AnAction configureShortcut = new AnAction() {
      { getTemplatePresentation().setText("Configure shortcut"); }
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Component component = e.getDataContext().getData(PlatformDataKeys.CONTEXT_COMPONENT);
        if (component == null) {
          Window[] frames = Window.getWindows();
          component = frames == null || frames.length == 0 ? null : frames[0];
          if (component == null) {
            LOG.error("can't show KeyboardShortcutDialog (parent component wasn't found)");
            return;
          }
        }

        KeymapPanel.addKeyboardShortcut(actionId, ActionShortcutRestrictions.getInstance().getForActionId(actionId), keymap, component, conflicted, SystemShortcuts.this);
        updateKeymapConflicts(myKeymap);
        if (getUnmutedConflictsCount() == 0)
          notification.expire();
      }
    };
    notification.addAction(configureShortcut);

    if (SystemInfo.isMac) {
      final AnAction changeSystemSettings = new AnAction() {
        { getTemplatePresentation().setText("Change system settings"); }
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ApplicationManager.getApplication().executeOnPooledThread(()->{
            final GeneralCommandLine cmdLine = new GeneralCommandLine(
              "osascript",
              "-e", "tell application \"System Preferences\"",
              "-e", "set the current pane to pane id \"com.apple.preference.keyboard\"",
              "-e", "reveal anchor \"shortcutsTab\" of pane id \"com.apple.preference.keyboard\"",
              "-e", "activate",
              "-e", "end tell");
            try {
              ExecUtil.execAndGetOutput(cmdLine);
              // NOTE: we can't detect OS-settings changes
              // but we can try to schedule check conflicts (and expire notification if necessary)
            } catch (ExecutionException ex) {
              LOG.error(ex);
            }
          });
        }
      };
      notification.addAction(changeSystemSettings);
    }

    final AnAction muteAction = new AnAction() {
      { getTemplatePresentation().setText("Don't show again for " + actionId); }
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myMutedConflicts.addMutedAction(actionId);
      }
    };
    notification.addAction(muteAction);

    if (getUnmutedConflictsCount() > 1) {
      final AnAction showKeymapPanelAction = new AnAction() {
        { getTemplatePresentation().setText("Show all conflicts"); }
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          new EditKeymapsDialog(null, actionId, true).show();
          updateKeymapConflicts(myKeymap);
          if (getUnmutedConflictsCount() == 0)
            notification.expire();
        }
      };
      notification.addAction(showKeymapPanelAction);
    }

    notification.notify(null);
  }

  private static @NotNull String getDescription(@NotNull AWTKeyStroke systemHotkey) {
    Class shkClass = ReflectionUtil.forName("java.awt.desktop.SystemHotkey");
    if (shkClass == null)
      return ourUnknownSysAction;

    final Method method = ReflectionUtil.getMethod(shkClass, "getDescription");
    String result = null;
    try {
      result = (String)method.invoke(systemHotkey);
    } catch (Throwable e) {
      Logger.getInstance(SystemShortcuts.class).error(e);
    }
    return result == null ? ourUnknownSysAction : result;
  }

  private void readSystem() {
    myKeyStroke2SysShortcut.clear();

    if (!SystemInfo.isMac || !SystemInfo.isJetBrainsJvm)
      return;

    try {
      if (!Registry.is("read.system.shortcuts"))
        return;

      Class shkClass = ReflectionUtil.forName("java.awt.desktop.SystemHotkey");
      if (shkClass == null)
        return;

      final Method method = ReflectionUtil.getMethod(shkClass, "readSystemHotkeys");
      if (method == null)
        return;

      List<AWTKeyStroke> all = (List<AWTKeyStroke>)method.invoke(shkClass);
      if (all == null || all.isEmpty())
        return;

      for (AWTKeyStroke shk: all) {
        if (shk.getModifiers() == 0) {
          //System.out.println("Skip system shortcut [without modifiers]: " + shk);
          continue;
        }
        if (shk.getKeyChar() == KeyEvent.CHAR_UNDEFINED && shk.getKeyCode() == KeyEvent.VK_UNDEFINED) {
          //System.out.println("Skip system shortcut [undefined key]: " + shk);
          continue;
        }
        KeyStroke sysKS;
        if (shk.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
          final int keyCode = KeyEvent.getExtendedKeyCodeForChar(shk.getKeyChar());
          if (keyCode == KeyEvent.VK_UNDEFINED) {
            //System.out.println("Skip system shortcut [undefined key]: " + shk);
            continue;
          }
          sysKS = KeyStroke.getKeyStroke(keyCode, shk.getModifiers());
        } else
          sysKS = KeyStroke.getKeyStroke(shk.getKeyCode(), shk.getModifiers());

        myKeyStroke2SysShortcut.put(sysKS, shk);
      }
    } catch (Throwable e) {
      Logger.getInstance(SystemShortcuts.class).debug(e);
    }
  }

  private static class MuteConflictsSettings {
    private static final String MUTED_ACTIONS_KEY = "muted.system.shortcut.conflicts.actions";
    private final @NotNull Set<String> myMutedActions = new HashSet<>();

    MuteConflictsSettings() {
      final String[] muted = PropertiesComponent.getInstance().getValues(MUTED_ACTIONS_KEY);
      if (muted != null) {
        Collections.addAll(myMutedActions, muted);
      }
    }

    void addMutedAction(@NotNull String actId) {
      myMutedActions.add(actId);
      PropertiesComponent.getInstance().setValues(MUTED_ACTIONS_KEY, ArrayUtilRt.toStringArray(myMutedActions));
    }

    void removeMutedAction(@NotNull String actId) {
      myMutedActions.remove(actId);
      PropertiesComponent.getInstance().setValues(MUTED_ACTIONS_KEY, ArrayUtilRt.toStringArray(myMutedActions));
    }

    public boolean isMutedAction(@NotNull String actionId) {
      return myMutedActions.contains(actionId);
    }
  }
}
