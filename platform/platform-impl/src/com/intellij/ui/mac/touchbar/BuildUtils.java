// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomePopupAction;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.mac.TouchbarDataKeys;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.*;

class BuildUtils {
  private static final Logger LOG = Logger.getInstance(Utils.class);

  // https://developer.apple.com/design/human-interface-guidelines/macos/touch-bar/touch-bar-visual-design/
  //
  // Spacing type	Width between controls
  // Default	        16px
  // Small fixed space	32px
  // Large fixed space	64px
  // Flexible space	Varies. Matches the available space.
  private static final String ourSmallSeparatorText = "type.small";
  private static final String ourLargeSeparatorText = "type.large";
  private static final String ourFlexibleSeparatorText = "type.flexible";

  private static final int ourRunConfigurationPopoverWidth = 143;
  private static final int BUTTON_MIN_WIDTH_DLG = 107;
  private static final int BUTTON_BORDER = 16;
  private static final int BUTTON_IMAGE_MARGIN = 2;

  private static final String RUNNERS_GROUP_TOUCHBAR = "RunnerActionsTouchbar";

  static {
    _initExecutorsGroup();
  }

  static void addActionGroupButtons(ItemsContainer out,
                                    ActionGroup actionGroup,
                                    ModalityState modality,
                                    int showMode,
                                    String filterGroupPrefix,
                                    String optionalCtxName,
                                    boolean forceUpdateOptionCtx) {
    _traverse(actionGroup, new IGroupVisitor() {
      private int mySeparatorCounter = 0;
      private LinkedList<Pair<ActionGroup, String>> myNodePath = new LinkedList<>();

      @Override
      public boolean enterNode(@NotNull ActionGroup groupNode) {
        final String groupName = getActionId(groupNode);
        if (filterGroupPrefix != null && groupName != null && groupName.startsWith(filterGroupPrefix))
          return false;

        myNodePath.add(Pair.create(groupNode, groupName));
        return true;
      }

      @Override
      public void visitLeaf(AnAction act) {
        if (act instanceof Separator) {
          final Separator sep = (Separator)act;
          int increment = 1;
          if (sep.getText() != null) {
            if (sep.getText().equals(ourSmallSeparatorText))
              out.addSpacing(false);
            else if (sep.getText().equals(ourLargeSeparatorText))
              out.addSpacing(true);
            else if (sep.getText().equals(ourFlexibleSeparatorText))
              out.addFlexibleSpacing();
          } else {
            mySeparatorCounter += increment;
          }
          return;
        }
        if (mySeparatorCounter > 0) {
          if (mySeparatorCounter == 1) out.addSpacing(false);
          else if (mySeparatorCounter == 2) out.addSpacing(true);
          else out.addFlexibleSpacing();

          mySeparatorCounter = 0;
        }

        final String actId = getActionId(act);
        // if (actId == null || actId.isEmpty())   System.out.println("unregistered action: " + act);

        final boolean isRunConfigPopover = "RunConfiguration".equals(actId); // TODO: make more intelligence customizing...
        final boolean isOpenInTerminalAction = "Terminal.OpenInTerminal".equals(actId);
        final int mode = isRunConfigPopover || isOpenInTerminalAction ? TBItemAnActionButton.SHOWMODE_IMAGE_TEXT : showMode;
        final TBItemAnActionButton butt = out.addAnActionButton(act, mode, modality);
        if (_isCompactParent())
          butt.setHiddenWhenDisabled(true);
        if (forceUpdateOptionCtx || _isOptionalParent())
          butt.myOptionalContextName = optionalCtxName;

        if (isRunConfigPopover) {
          butt.setWidth(ourRunConfigurationPopoverWidth);
          butt.setHasArrowIcon(true);
        } else if (act instanceof WelcomePopupAction)
          butt.setHasArrowIcon(true);
      }

      @Override
      public void leaveNode(ActionGroup groupNode) { myNodePath.removeLast(); }

      private boolean _isCompactParent() { return !myNodePath.isEmpty() && myNodePath.getLast().first instanceof CompactActionGroup; }
      private boolean _isOptionalParent() {
        if (myNodePath.isEmpty())
          return false;
        final String gname = myNodePath.getLast().second;
        return optionalCtxName != null && optionalCtxName.equals(gname);
      }
    });
  }

  static ActionGroup getCustomizedGroup(@NotNull String barId) {
    final ActionGroup actGroup = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_TOUCHBAR);
    final AnAction[] kids = actGroup.getChildren(null);
    final String childGroupId = barId.startsWith(IdeActions.GROUP_TOUCHBAR) ? barId : IdeActions.GROUP_TOUCHBAR + barId;

    for (AnAction act : kids) {
      if (!(act instanceof ActionGroup))
        continue;
      final String gid = getActionId(act);
      if (gid == null || gid.isEmpty()) {
        LOG.error("unregistered ActionGroup: " + act);
        continue;
      }
      if (gid.equals(childGroupId))
        return (ActionGroup)act;
    }

    return null;
  }

  static Map<String, ActionGroup> getAltLayouts(@NotNull ActionGroup context) {
    final String ctxId = getActionId(context);
    if (ctxId == null || ctxId.isEmpty()) {
      LOG.error("can't load alt-layout for unregistered ActionGroup: " + context);
      return null;
    }

    Map<String, ActionGroup> result = new HashMap<>();
    final AnAction[] kids = context.getChildren(null);
    for (AnAction act : kids) {
      if (!(act instanceof ActionGroup))
        continue;
      final String gid = getActionId(act);
      if (gid == null || gid.isEmpty()) {
        LOG.info("skip loading alt-layout for unregistered ActionGroup: " + act + ", child of " + context);
        continue;
      }
      if (gid.startsWith(ctxId + "_"))
        result.put(gid.substring(ctxId.length() + 1), (ActionGroup)act);
    }

    return result;
  }

  static TouchBar createMessageDlgBar(@NotNull String[] buttons, @NotNull Runnable[] actions, String defaultButton) {
    final TouchBar result = new TouchBar("message_dlg_bar", false, false, false);
    final TBItemGroup gr = result.addGroup();
    final ItemsContainer group = gr.getContainer();

    // NOTE: buttons are placed from right to left, see SheetController.layoutButtons
    int defIndex = -1;
    if (defaultButton != null)
      defaultButton = defaultButton.trim();
    final int len = Math.min(buttons.length, actions.length);
    for (int c = len - 1; c >= 0; --c) {
      final String sb = buttons[c];
      final boolean isDefault = Comparing.equal(sb, defaultButton);
      if (isDefault) {
        defIndex = c;
        continue;
      }
      final TBItemButton tbb = group.addButton().setText(DialogWrapper.extractMnemonic(sb).second).setThreadSafeAction(actions[c]);
      _setDialogLayout(tbb);
    }

    if (defIndex >= 0) {
      final TBItemButton tbb = group.addButton().setText(DialogWrapper.extractMnemonic(buttons[defIndex]).second).setThreadSafeAction(actions[defIndex]).setColored(true);
      _setDialogLayout(tbb);
    }

    result.setPrincipal(gr);
    result.selectVisibleItemsToShow();
    return result;
  }

  static TouchBar createButtonsBar(@NotNull Map<TouchbarDataKeys.DlgButtonDesc, JButton> unorderedButtons) {
    final TouchBar result = new TouchBar("dialog_buttons", false, false, false);
    final ModalityState ms = LaterInvocator.getCurrentModalityState();

    // 1. add option buttons (at left)
    byte prio = -1;
    for (JButton jb: unorderedButtons.values()) {
      TBItemButton tbb = null;
      if (jb instanceof JBOptionButton) {
        final JBOptionButton ob = (JBOptionButton)jb;
        final Action[] opts = ob.getOptions();
        for (Action a : opts) {
          if (a == null)
            continue;
          final AnAction anAct = _createAnAction(a, ob, true);
          if (anAct == null)
            continue;

          // NOTE: must set different priorities for items, otherwise system can hide all items with the same priority (but some of them is able to be placed)
          tbb = result.addAnActionButton(anAct, TBItemAnActionButton.SHOWMODE_TEXT_ONLY, ms).setComponent(ob).setPriority(--prio);
        }
      }

      if (tbb != null)
        _setDialogLayout(tbb);
    }

    // 2. add left and main buttons (and make main principal)
    final TBItemGroup gr = result.addGroup();
    final ItemsContainer group = gr.getContainer();
    final List<TouchbarDataKeys.DlgButtonDesc> ordered = _extractOrderedButtons(unorderedButtons);
    for (TouchbarDataKeys.DlgButtonDesc desc: ordered) {
      final JButton jb = unorderedButtons.get(desc);

      // NOTE: can be true: jb.getAction().isEnabled() && !jb.isEnabled()
      final AnAction anAct = _createAnAction(jb.getAction(), jb, false);
      if (anAct == null)
        continue;
      final TBItemButton tbb = desc.isMainGroup ?
                               group.addAnActionButton(anAct, TBItemAnActionButton.SHOWMODE_TEXT_ONLY, ms).setComponent(jb) :
                               result.addAnActionButton(anAct, TBItemAnActionButton.SHOWMODE_TEXT_ONLY, ms, gr).setComponent(jb).setPriority(--prio);

      boolean isDefault = desc.isDefalut;
      if (!isDefault) {
        // check other properties
        isDefault = jb.getAction() != null ? jb.getAction().getValue(DialogWrapper.DEFAULT_ACTION) != null : jb.isDefaultButton();
      }
      if (isDefault)
        tbb.setColored(true);
      _setDialogLayout(tbb);
    }

    result.selectVisibleItemsToShow();
    result.setPrincipal(gr);
    return result;
  }

  // creates releaseOnClose touchbar
  static TouchBar createScrubberBarFromPopup(@NotNull ListPopupImpl listPopup) {
    final TouchBar result = new TouchBar("popup_scrubber_bar" + listPopup, true, false, true);

    final TBItemScrubber scrub = result.addScrubber();
    final ModalityState ms = LaterInvocator.getCurrentModalityState();
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

      scrub.addItem(ic, txt, () -> ApplicationManager.getApplication().invokeLater(() -> action.run(), ms));
    }

    result.selectVisibleItemsToShow();
    return result;
  }

  // creates releaseOnClose touchbar
  static TouchBar createStopRunningBar(List<Pair<RunContentDescriptor, Runnable>> stoppableDescriptors) {
    final TouchBar tb = new TouchBar("select_running_configuration_to_stop", true, true, true);
    tb.addButton().setText("Stop All").setActionOnEDT(() -> {
      stoppableDescriptors.forEach((pair) -> { pair.second.run(); });
    });
    final TBItemScrubber stopScrubber = tb.addScrubber();
    for (Pair<RunContentDescriptor, Runnable> sd : stoppableDescriptors)
      stopScrubber.addItem(sd.first.getIcon(), sd.first.getDisplayName(), sd.second);
    tb.selectVisibleItemsToShow();
    return tb;
  }

  private static void _setDialogLayout(TBItemButton button) {
    if (button == null)
      return;
    button.setLayout(BUTTON_MIN_WIDTH_DLG, NSTLibrary.LAYOUT_FLAG_MIN_WIDTH, BUTTON_IMAGE_MARGIN, BUTTON_BORDER);
  }

  interface IGroupVisitor {
    boolean enterNode(ActionGroup groupNode); // returns false when must skip node
    void visitLeaf(AnAction leaf);
    void leaveNode(ActionGroup groupNode);
  }

  static String getActionId(AnAction act) {
    return ActionManager.getInstance().getId(act instanceof CustomisedActionGroup ? ((CustomisedActionGroup)act).getOrigin() : act);
  }

  private static void _traverse(@NotNull ActionGroup group, @NotNull IGroupVisitor visitor) {
    String groupId = getActionId(group);
    if (groupId == null) groupId = "unregistered";

    final AnAction[] children = group.getChildren(null);
    for (int i = 0; i < children.length; i++) {
      AnAction child = children[i];
      if (child == null) {
        LOG.error(String.format("action is null: i=%d, group='%s', group id='%s'",i, group.toString(), groupId));
        continue;
      }

      if (child instanceof ActionGroup) {
        final @NotNull ActionGroup childGroup = (ActionGroup)child;
        final boolean visitNode = visitor.enterNode(childGroup);
        if (!visitNode) {
          // System.out.printf("filter child group: i=%d, childId='%s', group='%s', group id='%s'\n", i, _getActionId(child), group.toString(), groupId);
          continue;
        }

        //if (actionGroup.isPopup()) System.out.println(String.format("add child with isPopup=true: i=%d, childId='%s', group='%s', group id='%s'", i, childId, group.toString(), groupId));
        try {
          _traverse((ActionGroup)child, visitor);
        } finally {
          visitor.leaveNode(childGroup);
        }
      } else
        visitor.visitLeaf(child);
    }
  }

  private static AnAction _createAnAction(@Nullable Action action, @NotNull JButton fromButton, boolean useTextFromAction /*for optional buttons*/) {
    final Object anAct = action == null ? null : action.getValue(OptionAction.AN_ACTION);
    if (anAct == null) {
      // LOG.warn("null AnAction in action: '" + action + "', use wrapper");
      return new DumbAwareAction() {
        {
          setEnabledInModalContext(true);
          if (useTextFromAction) {
            final Object name = action == null ? fromButton.getText() : action.getValue(Action.NAME);
            getTemplatePresentation().setText(name != null && name instanceof String ? (String)name : "");
          }
        }
        @Override
        public void actionPerformed(AnActionEvent e) {
          if (action == null) {
            fromButton.doClick();
            return;
          }
          // also can be used something like: ApplicationManager.getApplication().invokeLater(() -> jb.doClick(), ms)
          action.actionPerformed(new ActionEvent(fromButton, ActionEvent.ACTION_PERFORMED, null));
        }
        @Override
        public void update(AnActionEvent e) {
          e.getPresentation().setEnabled(action == null ? fromButton.isEnabled() : action.isEnabled());
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

  private static @Nullable List<TouchbarDataKeys.DlgButtonDesc> _extractOrderedButtons(@NotNull Map<TouchbarDataKeys.DlgButtonDesc, JButton> unorderedButtons) {
    if (unorderedButtons.isEmpty())
      return null;

    final List<TouchbarDataKeys.DlgButtonDesc> main = new ArrayList<>();
    final List<TouchbarDataKeys.DlgButtonDesc> secondary = new ArrayList<>();
    for (Map.Entry<TouchbarDataKeys.DlgButtonDesc, JButton> entry: unorderedButtons.entrySet()) {
      final TouchbarDataKeys.DlgButtonDesc jbdesc = entry.getKey();
      if (jbdesc == null)
        continue;

      if (jbdesc.isMainGroup)
        main.add(jbdesc);
      else
        secondary.add(jbdesc);
    }

    final Comparator<TouchbarDataKeys.DlgButtonDesc> cmp = (desc1, desc2) -> Integer.compare(desc1.orderIndex, desc2.orderIndex);
    main.sort(cmp);
    secondary.sort(cmp);
    return ContainerUtil.concat(secondary, main);
  }

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
