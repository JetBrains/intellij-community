// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

@Service
public final class SystemShortcuts {
  private static final Logger LOG = Logger.getInstance(SystemShortcuts.class);
  private static final @NotNull NotificationGroup GROUP = NotificationGroupManager.getInstance().getNotificationGroup("System shortcuts conflicts");
  private static final @NotNull String ourUnknownSysAction = "Unknown action";

  private @NotNull final Map<KeyStroke, AWTKeyStroke> myKeyStroke2SysShortcut = new HashMap<>();
  private @NotNull final Map<KeyStroke, String> myKeyStrokeCustomDescription = new HashMap<>();
  private @NotNull final MuteConflictsSettings myMutedConflicts = new MuteConflictsSettings();
  private @NotNull final Set<String> myNotifiedActions = new HashSet<>();
  private int myNotifyCount = 0;

  private @Nullable Keymap myKeymap;

  @NotNull
  private final Map<AWTKeyStroke, ConflictItem> myKeymapConflicts = new HashMap<>();

  private SystemShortcuts() {
    readSystem();
  }

  @NotNull
  public static SystemShortcuts getInstance() {
    return ApplicationManager.getApplication().getService(SystemShortcuts.class);
  }

  public static final class ConflictItem {
    final @NotNull String mySysActionDesc;
    final @NotNull KeyStroke mySysKeyStroke;
    final String @NotNull [] myActionIds;

    public ConflictItem(@NotNull KeyStroke sysKeyStroke, @NotNull String sysActionDesc, String @NotNull [] actionIds) {
      mySysKeyStroke = sysKeyStroke;
      mySysActionDesc = sysActionDesc;
      myActionIds = actionIds;
    }

    @NotNull
    public String getSysActionDesc() { return mySysActionDesc; }

    @NotNull
    public KeyStroke getSysKeyStroke() { return mySysKeyStroke; }

    public String @NotNull [] getActionIds() { return myActionIds; }

    @Nullable
    String getUnmutedActionId(@NotNull MuteConflictsSettings settings) {
      for (String actId : myActionIds) {
        if (!settings.isMutedAction(actId)) {
          return actId;
        }
      }
      return null;
    }
  }

  public void updateKeymapConflicts(@Nullable Keymap keymap) {
    myKeymap = keymap;
    myKeymapConflicts.clear();

    if (myKeyStroke2SysShortcut.isEmpty()) {
      return;
    }

    for (@NotNull KeyStroke sysKS : myKeyStroke2SysShortcut.keySet()) {
      final String[] actIds = computeOnEdt(() -> keymap.getActionIds(sysKS));
      if (actIds == null || actIds.length == 0) {
        continue;
      }

      @NotNull AWTKeyStroke shk = myKeyStroke2SysShortcut.get(sysKS);
      myKeymapConflicts.put(shk, new ConflictItem(sysKS, getDescription(shk), actIds));
    }
  }

  @NotNull
  public Collection<ConflictItem> getUnmutedKeymapConflicts() {
    List<ConflictItem> result = new ArrayList<>();
    myKeymapConflicts.forEach((ks, ci) -> {
      if (ci.getUnmutedActionId(myMutedConflicts) != null) {
        result.add(ci);
      }
    });
    return result;
  }

  @Nullable
  public Condition<AnAction> createKeymapConflictsActionFilter() {
    if (myKeyStroke2SysShortcut.isEmpty() || myKeymap == null) {
      return null;
    }

    final Condition<Shortcut> predicat = sc -> {
      if (sc == null) {
        return false;
      }
      for (KeyStroke ks : myKeyStroke2SysShortcut.keySet()) {
        if (sc.startsWith(new KeyboardShortcut(ks, null))) {
          final ConflictItem ci = myKeymapConflicts.get(myKeyStroke2SysShortcut.get(ks));
          if (ci != null && ci.getUnmutedActionId(myMutedConflicts) != null) {
            return true;
          }
        }
      }
      return false;
    };
    return ActionsTreeUtil.isActionFiltered(ActionManager.getInstance(), myKeymap, predicat);
  }

  public @Nullable
  Map<KeyboardShortcut, String> calculateConflicts(@NotNull Keymap keymap, @NotNull String actionId) {
    if (myKeyStroke2SysShortcut.isEmpty()) {
      return null;
    }

    Map<KeyboardShortcut, String> result = null;
    final Shortcut[] actionShortcuts = computeOnEdt(() -> keymap.getShortcuts(actionId));
    for (Shortcut sc : actionShortcuts) {
      if (!(sc instanceof KeyboardShortcut)) {
        continue;
      }
      final KeyboardShortcut ksc = (KeyboardShortcut)sc;
      for (@NotNull KeyStroke sks : myKeyStroke2SysShortcut.keySet()) {
        if (ksc.getFirstKeyStroke().equals(sks) || sks.equals(ksc.getSecondKeyStroke())) {
          if (result == null) result = new HashMap<>();
          result.put(ksc, getDescription(myKeyStroke2SysShortcut.get(sks)));
        }
      }
    }
    return result;
  }

  private static <T> T computeOnEdt(Supplier<? extends T> supplier) {
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

  public @Nullable
  Map<KeyStroke, String> createKeystroke2SysShortcutMap() {
    if (myKeyStroke2SysShortcut.isEmpty()) {
      return null;
    }

    final Map<KeyStroke, String> result = new HashMap<>();
    myKeyStroke2SysShortcut.forEach((ks, sysks) -> result.put(ks, getDescription(sysks)));
    return result;
  }

  private int getUnmutedConflictsCount() {
    if (myKeymapConflicts.isEmpty()) {
      return 0;
    }
    int result = 0;
    for (ConflictItem ci : myKeymapConflicts.values()) {
      if (ci.getUnmutedActionId(myMutedConflicts) != null) {
        result++;
      }
    }
    return result;
  }

  public void onUserPressedShortcut(@NotNull Keymap keymap, String @NotNull [] actionIds, @NotNull KeyboardShortcut ksc) {
    if (myNotifyCount > 0 || actionIds.length == 0) {
      return;
    }

    KeyStroke ks = ksc.getFirstKeyStroke();
    AWTKeyStroke sysKs = myKeyStroke2SysShortcut.get(ks);
    if (sysKs == null && ksc.getSecondKeyStroke() != null) {
      sysKs = myKeyStroke2SysShortcut.get(ks = ksc.getSecondKeyStroke());
    }
    if (sysKs == null) {
      return;
    }

    String unmutedActId = null;
    for (String actId : actionIds) {
      if (myNotifiedActions.contains(actId)) {
        continue;
      }
      if (!myMutedConflicts.isMutedAction(actId)) {
        unmutedActId = actId;
        break;
      }
    }
    if (unmutedActId == null) {
      return;
    }

    @Nullable String macOsShortcutAction = getDescription(sysKs);
    if (macOsShortcutAction == ourUnknownSysAction) {
      macOsShortcutAction = null;
    }
    //System.out.println(actionId + " shortcut '" + sysKS + "' "
    //                   + Arrays.toString(actionShortcuts) + " conflicts with macOS shortcut"
    //                   + (macOsShortcutAction == null ? "." : " '" + macOsShortcutAction + "'."));
    doNotify(keymap, unmutedActId, ks, macOsShortcutAction, ksc);
  }

  private void doNotify(@NotNull Keymap keymap,
                        @NotNull String actionId,
                        @NotNull KeyStroke sysKS,
                        @Nullable String macOsShortcutAction,
                        @NotNull KeyboardShortcut conflicted) {
    updateKeymapConflicts(keymap);
    final int unmutedConflicts = getUnmutedConflictsCount();
    final boolean hasOtherConflicts = unmutedConflicts > 1;

    final AnAction act = ActionManager.getInstance().getAction(actionId);
    final String actText = act == null ? actionId : act.getTemplateText(); // TODO: fix action ids from services domain
    final String message;
    if (hasOtherConflicts) {
      message = IdeBundle.message("notification.content.more.shortcut.conflict", actText, unmutedConflicts - 1);
    }
    else {
      message = IdeBundle.message("notification.content.shortcut.conflicts.with.macos.shortcut.modify", actText,
                                  macOsShortcutAction == null ? "" : " '" + macOsShortcutAction + "'");
    }

    final Notification notification =
      GROUP.createNotification(IdeBundle.message("notification.title.shortcuts.conflicts"), message, NotificationType.WARNING);

    if (hasOtherConflicts) {
      final AnAction showKeymapPanelAction = DumbAwareAction.create(IdeBundle.message("action.text.modify.shortcuts"), e -> {
                                                                      new EditKeymapsDialog(null, actionId, true).show();
                                                                      updateKeymapConflicts(myKeymap);
                                                                    }
      );
      notification.addAction(showKeymapPanelAction);
    }
    else {
      final AnAction configureShortcut = DumbAwareAction.create(IdeBundle.message("action.text.modify.shortcut"), e -> {
        Component component = e.getDataContext().getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
        if (component == null) {
          Window[] frames = Window.getWindows();
          component = frames == null || frames.length == 0 ? null : frames[0];
          if (component == null) {
            LOG.error("can't show KeyboardShortcutDialog (parent component wasn't found)");
            return;
          }
        }

        KeymapPanel.addKeyboardShortcut(
          actionId, ActionShortcutRestrictions.getInstance().getForActionId(actionId), keymap, component, conflicted, this);

        notification.expire();
      });
      notification.addAction(configureShortcut);
    }

    final AnAction muteAction = DumbAwareAction.create(IdeBundle.message("action.dont.show.again.text"), e -> {
      myMutedConflicts.addMutedAction(actionId);
      notification.expire();
    });
    notification.addAction(muteAction);

    if (SystemInfo.isMac && !hasOtherConflicts) {
      final AnAction changeSystemSettings = DumbAwareAction.create(IdeBundle.message("action.text.change.system.shortcuts"), e -> {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
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
          }
          catch (ExecutionException ex) {
            LOG.error(ex);
          }
        });
      });
      notification.addAction(changeSystemSettings);
    }

    myNotifiedActions.add(actionId);
    ++myNotifyCount;
    notification.notify(null);
  }

  private static Class<?> ourShkClass;
  private static Method ourMethodGetDescription;
  private static Method ourMethodReadSystemHotkeys;

  private @NotNull String getDescription(@NotNull AWTKeyStroke systemHotkey) {
    if (systemHotkey instanceof KeyStroke) {
      return myKeyStrokeCustomDescription.get(systemHotkey);
    }
    if (ourShkClass == null) {
      ourShkClass = ReflectionUtil.forName("java.awt.desktop.SystemHotkey");
    }

    if (ourMethodGetDescription == null) {
      ourMethodGetDescription = ReflectionUtil.getMethod(ourShkClass, "getDescription");
    }
    String result = null;
    try {
      result = (String)ourMethodGetDescription.invoke(systemHotkey);
    }
    catch (Throwable e) {
      Logger.getInstance(SystemShortcuts.class).error(e);
    }

    if (result == null) {
      return ourUnknownSysAction;
    }

    // shorten description when the result string looks like:
    // "com.apple.Safari - Search With %WebSearchProvider@ - searchWithWebSearchProvider"
    final String delimiter = " - ";
    final int pos0 = result.indexOf(delimiter);
    if (pos0 < 0) {
      return result;
    }
    final int pos1 = result.indexOf(delimiter, pos0 + delimiter.length());
    if (pos1 < 0) {
      return result;
    }

    return result.substring(pos0 + delimiter.length(), pos1).replace("%", "").replace("@", "");
  }

  private static final boolean DEBUG_SYSTEM_SHORTCUTS = Boolean.getBoolean("debug.system.shortcuts");

  private void readSystem() {
    myKeyStroke2SysShortcut.clear();

    if (!SystemInfo.isMac || !SystemInfo.isJetBrainsJvm) {
      return;
    }

    try {
      if (!Registry.is("read.system.shortcuts")) {
        return;
      }

      if (ourShkClass == null) {
        ourShkClass = ReflectionUtil.forName("java.awt.desktop.SystemHotkey");
      }

      if (ourMethodReadSystemHotkeys == null) {
        ourMethodReadSystemHotkeys = ReflectionUtil.getMethod(ourShkClass, "readSystemHotkeys");
      }
      if (ourMethodReadSystemHotkeys == null) {
        return;
      }

      @SuppressWarnings("unchecked")
      List<AWTKeyStroke> all = (List<AWTKeyStroke>)ourMethodReadSystemHotkeys.invoke(ourShkClass);
      if (all == null || all.isEmpty()) {
        return;
      }

      StringBuilder debugInfo = new StringBuilder();
      for (AWTKeyStroke shk : all) {
        if (shk.getModifiers() == 0) {
          //System.out.println("Skip system shortcut [without modifiers]: " + shk);
          continue;
        }
        if (shk.getKeyChar() == KeyEvent.CHAR_UNDEFINED && shk.getKeyCode() == KeyEvent.VK_UNDEFINED) {
          //System.out.println("Skip system shortcut [undefined key]: " + shk);
          continue;
        }
        if ("Move focus to the next window in application".equals(getDescription(shk))) {
          // Skip this shortcut because it handled in IDE-side
          // see: JBR-1515 Regression test jb/sun/awt/macos/MoveFocusShortcutTest.java fails on macOS  (Now we prevent Mac OS from handling the shortcut. We can enumerate windows on IDE level.)
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
        }
        else {
          sysKS = KeyStroke.getKeyStroke(shk.getKeyCode(), shk.getModifiers());
        }

        myKeyStroke2SysShortcut.put(sysKS, shk);

        if (DEBUG_SYSTEM_SHORTCUTS) {
          debugInfo.append(shk.toString()).append(";\n");
        }
      }
      if (DEBUG_SYSTEM_SHORTCUTS) {
        Logger.getInstance(SystemShortcuts.class).info("system shortcuts:\n" + debugInfo);
      }
      if (SystemInfo.isMacOSBigSur) {
        addCustomShortcut(KeyEvent.VK_OPEN_BRACKET, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, "Select Next Tab Window");
        addCustomShortcut(KeyEvent.VK_CLOSE_BRACKET, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, "Select Previous Tab Window");
      }
    }
    catch (Throwable e) {
      Logger.getInstance(SystemShortcuts.class).debug(e);
    }
  }

  private void addCustomShortcut(int keycode, int modifiers, @NotNull String description) {
    KeyStroke newStroke = KeyStroke.getKeyStroke(keycode, modifiers);

    for (KeyStroke keyStroke : myKeyStroke2SysShortcut.keySet()) {
      if (newStroke.equals(keyStroke)) {
        return;
      }
    }

    myKeyStroke2SysShortcut.put(newStroke, newStroke);
    myKeyStrokeCustomDescription.put(newStroke, description);
  }

  private static final class MuteConflictsSettings {
    private static final String MUTED_ACTIONS_KEY = "muted.system.shortcut.conflicts.actions";
    @NotNull
    private Set<String> myMutedActions;

    void init() {
      if (myMutedActions != null) {
        return;
      }
      myMutedActions = new HashSet<>();
      List<String> muted = PropertiesComponent.getInstance().getList(MUTED_ACTIONS_KEY);
      if (muted != null) {
        myMutedActions.addAll(muted);
      }
    }

    void addMutedAction(@NotNull String actId) {
      init();
      myMutedActions.add(actId);
      PropertiesComponent.getInstance().setList(MUTED_ACTIONS_KEY, myMutedActions);
    }

    void removeMutedAction(@NotNull String actId) {
      init();
      myMutedActions.remove(actId);
      PropertiesComponent.getInstance().setList(MUTED_ACTIONS_KEY, myMutedActions);
    }

    public boolean isMutedAction(@NotNull String actionId) {
      init();
      return myMutedActions.contains(actionId);
    }
  }
}