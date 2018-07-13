// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomePopupAction;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.mac.TouchbarDataKeys;
import com.intellij.ui.mac.UpdatableDefaultActionGroup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

class BuildUtils {
  private static final Logger LOG = Logger.getInstance(Utils.class);
  private static final String DIALOG_ACTIONS_CONTEXT = "DialogWrapper.touchbar.actions";

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

  static void addActionGroupButtons(@NotNull TouchBar out, @NotNull ActionGroup actionGroup, @Nullable String filterGroupPrefix, @Nullable Customizer customizer) {
    _traverse(actionGroup, new GroupVisitor(out, filterGroupPrefix, customizer));
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

  static void addDialogButtons(@NotNull TouchBar out, @Nullable Map<TouchbarDataKeys.DlgButtonDesc, JButton> unorderedButtons, @Nullable Map<Component, ActionGroup> actions) {
    final ModalityState ms = Utils.getCurrentModalityState();
    final boolean hasSouthPanelButtons = unorderedButtons != null && !unorderedButtons.isEmpty();
    final byte[] prio = {-1};

    // 1. add (at left) option buttons of dialog (from south panel)
    if (hasSouthPanelButtons) {
      for (JButton jb : unorderedButtons.values()) {
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
            tbb = out.addAnActionButton(anAct).setShowMode(TBItemAnActionButton.SHOWMODE_TEXT_ONLY).setModality(ms).setComponent(ob).setPriority(--prio[0]);
          }
        }

        if (tbb != null)
          _setDialogLayout(tbb);
      }
    }

    // 2. add actions (collected from content children) before principal group
    if (actions != null && !actions.isEmpty()) {
      final ActionGroup ag = actions.values().iterator().next();

      final TouchbarDataKeys.ActionDesc groupDesc = ag.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
      if (
        !hasSouthPanelButtons ||
        (groupDesc != null && groupDesc.isCombineWithDlgButtons())
      ) {
        final Customizer customizer = new Customizer(groupDesc, ms) {
          @Override
          public void process(@NotNull INodeInfo ni, @NotNull TBItemAnActionButton butt) {
            super.process(ni, butt);
            butt.setPriority(--prio[0]);
            butt.myOptionalContextName = DIALOG_ACTIONS_CONTEXT;
            if (ni.getParentDesc() != null && !ni.getParentDesc().isShowImage())
              butt.setShowMode(TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
          }
        };
        TouchBar.addActionGroup(out, ag, customizer);

        ag.addPropertyChangeListener(new PropertyChangeListener() {
          @Override
          public void propertyChange(PropertyChangeEvent evt) {
            if (!UpdatableDefaultActionGroup.PROP_CHILDREN.equals(evt.getPropertyName()))
              return;

            final AnAction[] prevArr = (AnAction[])evt.getOldValue();
            final AnAction[] currArr = (AnAction[])evt.getNewValue();

            final List<AnAction> prev = new ArrayList<>();
            for (AnAction act: prevArr)
              _collectActions(act, prev);
            final List<AnAction> curr = new ArrayList<>();
            for (AnAction act: currArr)
              _collectActions(act, curr);

            if (prev != null && !prev.isEmpty()) {
              if (curr != null && !curr.isEmpty()) {
                // System.out.printf("replace %d old actions with %d new inside: %s\n", prev.size(), curr.size(), out);
                if (curr.size() != prev.size()) {
                  out.getItemsContainer().remove(tbi -> DIALOG_ACTIONS_CONTEXT.equals(tbi.myOptionalContextName));
                  // System.out.printf("mismatch sizes of prev %d and cur %d actions inside: %s\n", prev.size(), curr.size(), out);
                  TouchBar.addActionGroup(out, ag, customizer);
                } else {
                  out.getItemsContainer().forEachDeep(tbi -> {
                    if (!(tbi instanceof TBItemAnActionButton))
                      return;

                    final TBItemAnActionButton butt = (TBItemAnActionButton)tbi;
                    final int prevIndex = prev.indexOf(butt.getAnAction());
                    if (prevIndex == -1)
                      return;

                    final AnAction newAct = curr.get(prevIndex);
                    // System.out.printf("prev act '%s', new act '%s'\n", getActionId(butt.getAnAction()), getActionId(newAct));
                    butt.setAnAction(newAct);
                  });
                }
              } else
                out.getItemsContainer().remove(tbi -> tbi instanceof TBItemAnActionButton && prev.indexOf(((TBItemAnActionButton)tbi).getAnAction()) != -1);
            } else {
              if (curr == null || curr.isEmpty()) // nothing to do
                return;

              // System.out.println("prev actions is null, add all new actions into: " + out);
              TouchBar.addActionGroup(out, ag, customizer);
            }
            out.updateActionItems();
          }
        });
      }
    }

    // 3. add left and main buttons (and make main principal)
    if (hasSouthPanelButtons) {
      final TBItemGroup gr = out.addGroup();
      final ItemsContainer group = gr.getContainer();
      final List<TouchbarDataKeys.DlgButtonDesc> ordered = _extractOrderedButtons(unorderedButtons);
      for (TouchbarDataKeys.DlgButtonDesc desc : ordered) {
        final JButton jb = unorderedButtons.get(desc);

        // NOTE: can be true: jb.getAction().isEnabled() && !jb.isEnabled()
        final AnAction anAct = _createAnAction(jb.getAction(), jb, false);
        if (anAct == null)
          continue;
        final TBItemButton tbb = desc.isMainGroup() ?
                                 group.addAnActionButton(anAct).setShowMode(TBItemAnActionButton.SHOWMODE_TEXT_ONLY).setModality(ms).setComponent(jb) :
                                 out.addAnActionButton(anAct, gr).setShowMode(TBItemAnActionButton.SHOWMODE_TEXT_ONLY).setModality(ms).setComponent(jb)
                                    .setPriority(--prio[0]);

        boolean isDefault = desc.isDefault();
        if (!isDefault) {
          // check other properties
          isDefault = jb.getAction() != null ? jb.getAction().getValue(DialogWrapper.DEFAULT_ACTION) != null : jb.isDefaultButton();
        }
        if (isDefault)
          tbb.setColored(true);
        _setDialogLayout(tbb);
      }

      out.selectVisibleItemsToShow();
      out.setPrincipal(gr);
    }
  }

  // creates releaseOnClose touchbar
  static TouchBar createScrubberBarFromPopup(@NotNull ListPopupImpl listPopup) {
    final TouchBar result = new TouchBar("popup_scrubber_bar" + listPopup, true, false, true);

    final Application app = ApplicationManager.getApplication();
    final ModalityState ms = app != null ? LaterInvocator.getCurrentModalityState() : null;

    final TBItemScrubber scrub = result.addScrubber();
    final @NotNull ListPopupStep listPopupStep = listPopup.getListStep();
    final @NotNull List stepValues = listPopupStep.getValues();
    for (Object obj : stepValues) {
      final Icon ic = listPopupStep.getIconFor(obj);
      String txt = listPopupStep.getTextFor(obj);

      if (listPopupStep.isMnemonicsNavigationEnabled()) {
        final MnemonicNavigationFilter<Object> filter = listPopupStep.getMnemonicNavigationFilter();
        final int pos = filter == null ? -1 : filter.getMnemonicPos(obj);
        if (pos != -1)
          txt = txt.substring(0, pos) + txt.substring(pos + 1);
      }

      final Runnable edtAction = () -> {
        if (obj != null)
          listPopup.getList().setSelectedValue(obj, false);
        else
          listPopup.getList().setSelectedIndex(stepValues.indexOf(obj));
        listPopup.handleSelect(true);
      };

      final Runnable action = () -> {
        if (app == null)
          SwingUtilities.invokeLater(edtAction);
        else
          app.invokeLater(edtAction, ms);
      };
      scrub.addItem(ic, txt, action);
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

  static class GroupVisitor implements INodeInfo {
    private final @NotNull TouchBar myOut;
    private final @Nullable String myFilterByPrefix;
    private final @Nullable Customizer myCustomizer;

    private int mySeparatorCounter = 0;
    private LinkedList<InternalNode> myNodePath = new LinkedList<>();

    GroupVisitor(@NotNull TouchBar out, @Nullable String filterByPrefix, @Nullable Customizer customizer) {
      this.myOut = out;
      this.myFilterByPrefix = filterByPrefix;
      this.myCustomizer = customizer;
    }

    boolean enterNode(@NotNull ActionGroup groupNode) {
      final String groupName = getActionId(groupNode);
      if (myFilterByPrefix != null && groupName != null && groupName.startsWith(myFilterByPrefix))
        return false;

      final TouchbarDataKeys.ActionDesc groupDesc = groupNode.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
      final InternalNode node = new InternalNode(groupNode, groupName, groupDesc);
      if (groupDesc != null && groupDesc.isMainGroup()) {
        final TBItemGroup group = myOut.addGroup();
        node.groupContainer = group.getContainer();
        myOut.setPrincipal(group);
      }

      myNodePath.add(node);
      return true;
    }

    void visitLeaf(AnAction act) {
      ItemsContainer out = _getParentContainer();
      if (out == null)
        out = myOut.getItemsContainer();

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

      final TBItemAnActionButton butt = out.addAnActionButton(act);
      if (myCustomizer != null)
        myCustomizer.process(this, butt);
    }

    void leaveNode(ActionGroup groupNode) { myNodePath.removeLast(); }

    private @Nullable ItemsContainer _getParentContainer() { return myNodePath.isEmpty() ? null : myNodePath.getLast().groupContainer; }

    @Override
    public boolean isParentCompact() { return !myNodePath.isEmpty() && myNodePath.getLast().group instanceof CompactActionGroup; }

    @Override
    public String getParentGroupID() {
      if (myNodePath.isEmpty())
        return null;
      return myNodePath.getLast().groupID;
    }

    @Override
    public TouchbarDataKeys.ActionDesc getParentDesc() {
      if (myNodePath.isEmpty())
        return null;
      return myNodePath.getLast().groupDesc;
    }

    private static class InternalNode {
      final @NotNull ActionGroup group;
      final @Nullable String groupID;
      final TouchbarDataKeys.ActionDesc groupDesc;

      ItemsContainer groupContainer;

      InternalNode(@NotNull ActionGroup group, @Nullable String groupID, TouchbarDataKeys.ActionDesc groupDesc) {
        this.group = group;
        this.groupID = groupID;
        this.groupDesc = groupDesc;
      }
    }
  }

  interface INodeInfo {
    boolean isParentCompact();
    String getParentGroupID();
    TouchbarDataKeys.ActionDesc getParentDesc();
  }

  static class Customizer {
    private final int myShowMode;
    private final @Nullable ModalityState myModality;

    Customizer(int showMode, @Nullable ModalityState modality) {
      myShowMode = showMode;
      myModality = modality;
    }

    Customizer(@Nullable TouchbarDataKeys.ActionDesc groupDesc, @Nullable ModalityState modality) {
      myShowMode = groupDesc == null || !groupDesc.isShowText() ? TBItemAnActionButton.SHOWMODE_IMAGE_ONLY_IF_PRESENTED : TBItemAnActionButton.SHOWMODE_IMAGE_TEXT;
      myModality = modality;
    }

    Customizer() {
      myShowMode = TBItemAnActionButton.SHOWMODE_IMAGE_ONLY_IF_PRESENTED;
      myModality = null;
    }

    void process(@NotNull INodeInfo ni, @NotNull TBItemAnActionButton butt) {
      final String actId = getActionId(butt.getAnAction());
      // if (actId == null || actId.isEmpty())   System.out.println("unregistered action: " + act);

      final boolean isRunConfigPopover = "RunConfiguration".equals(actId);
      final boolean isOpenInTerminalAction = "Terminal.OpenInTerminal".equals(actId);
      final TouchbarDataKeys.ActionDesc pd = ni.getParentDesc();
      final int nodeMode = pd != null && pd.isShowText() ? TBItemAnActionButton.SHOWMODE_IMAGE_TEXT : myShowMode;
      final int mode = isRunConfigPopover || isOpenInTerminalAction ? TBItemAnActionButton.SHOWMODE_IMAGE_TEXT : nodeMode;
      butt.setShowMode(mode).setModality(myModality);
      if (ni.isParentCompact())
        butt.setHiddenWhenDisabled(true);

      if (isRunConfigPopover) {
        butt.setHasArrowIcon(true);
        butt.setLayout(ourRunConfigurationPopoverWidth, 0, 5, 8);
      } else if (butt.getAnAction() instanceof WelcomePopupAction)
        butt.setHasArrowIcon(true);

      final TouchbarDataKeys.ActionDesc actionDesc = butt.getAnAction().getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
      if (actionDesc != null && actionDesc.getContextComponent() != null)
        butt.setComponent(actionDesc.getContextComponent());
    }
  }

  static @Nullable String getActionId(AnAction act) {
    if (ApplicationManager.getApplication() == null)
      return null;
    return ActionManager.getInstance().getId(act instanceof CustomisedActionGroup ? ((CustomisedActionGroup)act).getOrigin() : act);
  }

  private static void _setDialogLayout(TBItemButton button) {
    if (button == null)
      return;
    button.setLayout(BUTTON_MIN_WIDTH_DLG, NSTLibrary.LAYOUT_FLAG_MIN_WIDTH, BUTTON_IMAGE_MARGIN, BUTTON_BORDER);
  }

  private static void _traverse(@NotNull ActionGroup group, @NotNull GroupVisitor visitor) {
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

      if (jbdesc.isMainGroup())
        main.add(jbdesc);
      else
        secondary.add(jbdesc);
    }

    final Comparator<TouchbarDataKeys.DlgButtonDesc> cmp = (desc1, desc2) -> Integer.compare(desc1.getOrder(), desc2.getOrder());
    main.sort(cmp);
    secondary.sort(cmp);
    return ContainerUtil.concat(secondary, main);
  }

  private static void _collectActions(@NotNull AnAction act, @NotNull List<AnAction> out) {
    if (act instanceof ActionGroup) {
      final ActionGroup group = (ActionGroup)act;
      final AnAction[] children = group.getChildren(null);
      for (int i = 0; i < children.length; i++) {
        final AnAction child = children[i];
        if (child == null)
          continue;

        if (child instanceof ActionGroup) {
          final @NotNull ActionGroup childGroup = (ActionGroup)child;
          _collectActions(childGroup, out);
        } else
          out.add(child);
      }
    } else
      out.add(act);
  }
}
