// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomePopupAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.util.HashMap;
import java.util.Map;

final class ActionsLoader {
  private static final Logger LOG = Logger.getInstance(ActionsLoader.class);
  private static final boolean ENABLE_FN_MODE = Boolean.getBoolean("touchbar.fn.mode.enable");
  private static int FN_WIDTH = Integer.getInteger("touchbar.fn.width", 68);

  private static final boolean TOOLWINDOW_CROSS_ESC = !Boolean.getBoolean("touchbar.toolwindow.esc");
  private static final boolean TOOLWINDOW_EMULATE_ESC = Boolean.getBoolean("touchbar.toolwindow.emulateesc");
  private static final boolean TOOLWINDOW_PERSISTENT = !Boolean.getBoolean("touchbar.toolwindow.nonpersistent");

  private static final String SETTINS_AUTOCLOSE_KEY = "touchbar.toolwindow.autoclose";
  private static final String DEFAULT_ACTION_GROUP = "TouchBarDefault";

  static {
    FN_WIDTH = Math.max(FN_WIDTH, 50);
    FN_WIDTH = Math.min(FN_WIDTH, 100);
  }

  private static String[] getAutoCloseActions(@NotNull String toolWindowId) {
    if (toolWindowId.isEmpty()) {
      return null;
    }

    // TODO: read setting from proper place (think where)
    final String propVal = System.getProperty(SETTINS_AUTOCLOSE_KEY + '.' + toolWindowId);
    if (propVal == null || propVal.isEmpty()) {
      return getAutoCloseActionsDefault(toolWindowId);
    }

    final String[] split = propVal.split(",");
    if (split.length == 0) {
      return null;
    }

    for (int c = 0; c < split.length; ++c) {
      split[c] = split[c].trim();
    }
    return split;
  }

  private static final int RUN_CONFIG_POPOVER_WIDTH = 143;

  static @Nullable Pair<Map<Long, ActionGroup>, Customizer> getProjectDefaultActionGroup() {
    if (ENABLE_FN_MODE) {
      LOG.debug("use FN-actions group for default actions");
      return getFnActionGroup();
    }

    @Nullable Map<Long, ActionGroup> defaultGroup = getActionGroup(DEFAULT_ACTION_GROUP);
    if (defaultGroup == null) {
      return null;
    }

    // Create hardcoded customizer for project-default touchbar
    // TODO: load customizer from xml or settings
    Customizer customizer = new Customizer(
      null /*project-default touchbar never replaces esc-button*/,
      null /*project-default touchbar mustn't be closed because of auto-close actions*/,
      (parentInfo, butt, presentation) -> {
        final String actId = ActionManager.getInstance().getId(butt.getAnAction());

        final boolean isRunConfigPopover = "RunConfiguration".equals(actId);
        final boolean isOpenInTerminalAction = "Terminal.OpenInTerminal".equals(actId) || "Terminal.OpenInReworkedTerminal".equals(actId);
        if (isRunConfigPopover || isOpenInTerminalAction) {
          butt.setText(presentation.getText());
          butt.setIconFromPresentation(presentation);
        } else {
          TouchbarActionCustomizations customizations = parentInfo == null ? null : parentInfo.getCustomizations();
          butt.setIconAndTextFromPresentation(presentation, customizations);
        }

        if (isRunConfigPopover) {
          if (presentation.getIcon() != AllIcons.General.Add) {
            butt.setHasArrowIcon(true);
            butt.setLayout(RUN_CONFIG_POPOVER_WIDTH, 0, 5, 8);
          } else {
            butt.setHasArrowIcon(false);
            butt.setLayout(0, 0, 5, 8);
          }
        } else if (butt.getAnAction() instanceof WelcomePopupAction) {
          butt.setHasArrowIcon(true);
        }
    });
    return Pair.create(defaultGroup, customizer);
  }

  static @Nullable Pair<Map<Long, ActionGroup>, Customizer> getToolWindowActionGroup(@NotNull String toolWindowId) {
    if ("Services".equals(toolWindowId)) {
      LOG.debug("Services tool-window will use action-group from debug tool window");
      toolWindowId = "Debug";
    }
    final @Nullable Map<Long, ActionGroup> actions = getActionGroup(IdeActions.GROUP_TOUCHBAR + toolWindowId);
    if (actions == null || actions.get(0L) == null) {
      LOG.debug("null action group (or it doesn't contain main-layout) for tool window: %s", toolWindowId);
      return null;
    }

    final Customizer customizer = new Customizer(
      TOOLWINDOW_CROSS_ESC ? new TBPanel.CrossEscInfo(TOOLWINDOW_EMULATE_ESC, TOOLWINDOW_PERSISTENT) : null,
      getAutoCloseActions(toolWindowId)
    );
    return Pair.create(actions, customizer);
  }

  static @Nullable Map<Long, ActionGroup> getActionGroup(@NotNull String groupId) {
    // 1. build full name of group
    final String fullGroupId = groupId.startsWith(IdeActions.GROUP_TOUCHBAR) ? groupId : IdeActions.GROUP_TOUCHBAR + groupId;

    // 2. read touchbar-actions from CustomActionsSchema and select proper child
    final ActionGroup allTouchbarActions = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_TOUCHBAR);
    if (allTouchbarActions == null) {
      LOG.debug("can't create touchbar because ActionGroup isn't defined: %s", IdeActions.GROUP_TOUCHBAR);
      return null;
    }

    final ActionManager actionManager = ActionManager.getInstance();
    ActionGroup actionGroup = null;
    for (AnAction act : allTouchbarActions.getChildren(null)) {
      if (!(act instanceof ActionGroup)) {
        continue;
      }
      String actId = actionManager.getId(act instanceof CustomisedActionGroup o ? o.getDelegate() : act);
      if (actId == null || actId.isEmpty()) {
        continue;
      }

      if (actId.equals(fullGroupId)) {
        actionGroup = (ActionGroup)act;
        break;
      }
    }

    // 3. when group wasn't found in CustomActionsSchema just read group from ActionManager
    if (actionGroup == null) {
      LOG.debug("group %s wasn't found in CustomActionsSchema, will obtain it directly from ActionManager", groupId);
      final AnAction act = actionManager.getAction(fullGroupId);
      if (!(act instanceof ActionGroup)) {
        LOG.debug("can't create touchbar because corresponding ActionGroup isn't defined: %s", groupId);
        return null;
      }
      actionGroup = (ActionGroup)act;
    }

    // 4. extract main layout and alternative layouts with modifers
    Map<Long, ActionGroup> result = new HashMap<>();
    final DefaultActionGroup mainLayout = new DefaultActionGroup();
    mainLayout.getTemplatePresentation().copyFrom(actionGroup.getTemplatePresentation()); // just for convenience debug
    result.put(0L, mainLayout);
    for (AnAction act : actionGroup.getChildren(null)) {
      if (!(act instanceof ActionGroup)) {
        mainLayout.addAction(act);
        continue;
      }
      String gid = actionManager.getId(act instanceof CustomisedActionGroup o ? o.getDelegate() : act);
      if (gid.startsWith(fullGroupId + "_")) {
        final long mask = _str2mask(gid.substring(fullGroupId.length() + 1));
        if (mask != 0) {
          result.put(mask, (ActionGroup)act);
        } else {
          LOG.debug("zero mask for group: %s", fullGroupId);
        }
      } else {
        mainLayout.addAction(act);
      }
    }

    return result;
  }

  static @NotNull Pair<Map<Long, ActionGroup>, Customizer> getFnActionGroup() {
    final DefaultActionGroup result = new DefaultActionGroup(IdeBundle.message("action.fn.keys.text"), false);
    for (int c = 1; c <= 12; ++c) {
      result.add(new FNKeyAction(c));
    }
    Map<Long, ActionGroup> ret = new HashMap<>();
    ret.put(0L, result);

    Customizer customizer = new Customizer(null, null, (parentInfo, butt, presentation) -> {
      if (butt.getAnAction() instanceof FNKeyAction act) {
        butt.setWidth(FN_WIDTH);
        butt.setIcon(null);
        final String hint = presentation.getText() == null || presentation.getText().isEmpty() ? " " : presentation.getText();
        butt.setText(String.format("F%d", act.getFN()), hint, act.isActionDisabled());
      }
    });
    return Pair.create(ret, customizer);
  }

  private static long _str2mask(@NotNull String modifierId) {
    if (!modifierId.contains(".")) {
      if (modifierId.equalsIgnoreCase("alt")) {
        return InputEvent.ALT_DOWN_MASK;
      }
      if (modifierId.equalsIgnoreCase("cmd")) {
        return InputEvent.META_DOWN_MASK;
      }
      if (modifierId.equalsIgnoreCase("ctrl")) {
        return InputEvent.CTRL_DOWN_MASK;
      }
      if (modifierId.equalsIgnoreCase("shift")) {
        return InputEvent.SHIFT_DOWN_MASK;
      }
      return 0;
    }

    final String[] spl = modifierId.split("\\.");
    long mask = 0;
    for (String sub : spl) {
      mask |= _str2mask(sub);
    }
    return mask;
  }

  // hardcoded default auto-close actions
  private static String[] getAutoCloseActionsDefault(@NotNull String toolWindowId) {
    if (
      toolWindowId.equals(ToolWindowId.DEBUG) ||
      toolWindowId.equals(ToolWindowId.RUN) ||
      toolWindowId.equals(ToolWindowId.SERVICES)
    ) {
      return new String[]{ "Stop" };
    }
    return null;
  }
}
