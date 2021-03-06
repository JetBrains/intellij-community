// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.actionSystem.impl.Utils.ActionGroupVisitor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class BuildUtils {
  private static final boolean SKIP_UPDATE_SLOW_ACTIONS = Boolean.getBoolean("mac.touchbar.skip.update.slow.actions");
  private static final boolean PERSISTENT_LIST_OF_SLOW_ACTIONS = Boolean.getBoolean("mac.touchbar.remember.slow.actions");
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

  private static final int BUTTON_MIN_WIDTH_DLG = 107;
  private static final int BUTTON_BORDER = 16;
  private static final int BUTTON_IMAGE_MARGIN = 2;

  static @NotNull TouchBar buildFromCustomizedGroup(@NotNull String touchbarName,
                                                    @NotNull ActionGroup customizedGroup,
                                                    boolean replaceEsc) {
    final @NotNull String groupId = getActionId(customizedGroup);

    final TouchBar result = new TouchBar(touchbarName, replaceEsc, false, false, customizedGroup, groupId + "_");

    final String filterPrefix = groupId + "_";
    final String defaultOptCtx = groupId + "OptionalGroup";
    result.setDefaultOptionalContextName(defaultOptCtx);
    final Customizer customizer = new Customizer() {
      @Override
      public void process(@NotNull INodeInfo ni, @NotNull TBItemAnActionButton butt) {
        super.process(ni, butt);
        if (defaultOptCtx.equals(ni.getParentGroupID())) {
          butt.myOptionalContextName = defaultOptCtx;
        }
      }
    };
    addActionGroupButtons(result, customizedGroup, filterPrefix, customizer);
    result.selectVisibleItemsToShow();
    return result;
  }

  static @NotNull TouchBar buildFromGroup(@NotNull @NonNls String touchbarName,
                                          @NotNull ActionGroup actions,
                                          boolean replaceEsc,
                                          boolean emulateESC) {
    final TouchbarDataKeys.ActionDesc groupDesc =
      actions.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
    if (groupDesc != null && !groupDesc.isReplaceEsc()) {
      replaceEsc = false;
    }
    final TouchBar result = new TouchBar(touchbarName, replaceEsc, false, emulateESC, actions, null);
    addActionGroup(result, actions);
    return result;
  }

  static void addActionGroup(@NotNull TouchBar result, @NotNull ActionGroup actions) {
    final @NotNull ModalityState ms = LaterInvocator.getCurrentModalityState();
    final @Nullable TouchbarDataKeys.ActionDesc groupDesc =
      actions.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
    final Customizer customizer = new Customizer(groupDesc, ms);
    addActionGroup(result, actions, customizer);
  }

  static void addActionGroup(@NotNull TouchBar result, @NotNull ActionGroup actions, @NotNull Customizer customizer) {
    addActionGroupButtons(result, actions, null, customizer);
    result.selectVisibleItemsToShow();
  }

  static void addActionGroupButtons(@NotNull TouchBar out,
                                    @NotNull ActionGroup actionGroup,
                                    @Nullable String filterGroupPrefix,
                                    @Nullable Customizer customizer) {
    GroupVisitor visitor = new GroupVisitor(out, filterGroupPrefix, customizer, out.getStats(), false);

    final DataContext dctx = DataManager.getInstance().getDataContext(getCurrentFocusComponent());
    Utils.expandActionGroup(false, actionGroup, out.getFactory(), dctx, ActionPlaces.TOUCHBAR_GENERAL, false, visitor);
    if (customizer != null) {
      customizer.finish();
    }
  }

  static @Nullable Component getCurrentFocusComponent() {
    final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component focusOwner = focusManager.getFocusOwner();
    if (focusOwner == null) {
      focusOwner = focusManager.getPermanentFocusOwner();
    }
    if (focusOwner == null) {
      // LOG.info(String.format("WARNING: [%s:%s] _getCurrentFocusContext: null focus-owner, use focused window", myUid, myActionId));
      return focusManager.getFocusedWindow();
    }
    return focusOwner;
  }

  static ActionGroup getCustomizedGroup(@NotNull String barId) {
    final ActionGroup actGroup = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_TOUCHBAR);
    final AnAction[] kids = actGroup.getChildren(null);
    final String childGroupId = barId.startsWith(IdeActions.GROUP_TOUCHBAR) ? barId : IdeActions.GROUP_TOUCHBAR + barId;

    for (AnAction act : kids) {
      if (!(act instanceof ActionGroup)) {
        continue;
      }
      final @NotNull String gid = getActionId(act);
      if (gid.equals(childGroupId)) {
        return (ActionGroup)act;
      }
    }

    return null;
  }

  static Map<String, ActionGroup> getAltLayouts(@NotNull ActionGroup context) {
    final @NotNull String ctxId = getActionId(context);

    Map<String, ActionGroup> result = new HashMap<>();
    final AnAction[] kids = context.getChildren(null);
    for (AnAction act : kids) {
      if (!(act instanceof ActionGroup)) {
        continue;
      }
      final @NotNull String gid = getActionId(act);
      if (gid.startsWith(ctxId + "_")) {
        result.put(gid.substring(ctxId.length() + 1), (ActionGroup)act);
      }
    }

    return result;
  }

  static void addDialogButtons(@NotNull TouchBar out,
                               @Nullable Map<TouchbarDataKeys.DlgButtonDesc, JButton> unorderedButtons,
                               @Nullable Map<Component, ActionGroup> actions) {
    final @NotNull ModalityState ms = LaterInvocator.getCurrentModalityState();
    final boolean hasSouthPanelButtons = unorderedButtons != null && !unorderedButtons.isEmpty();
    final byte[] prio = {-1};

    // 1. add (at left) option buttons of dialog (from south panel)
    if (hasSouthPanelButtons) {
      for (JButton jb : unorderedButtons.values()) {
        TBItemButton tbb = null;
        if (jb instanceof JBOptionButton) {
          final JBOptionButton ob = (JBOptionButton)jb;
          final Action[] opts = ob.getOptions();
          if (opts != null) {
            for (Action a : opts) {
              if (a == null) {
                continue;
              }
              final AnAction anAct = _createAnAction(a, ob, true);
              if (anAct == null) {
                continue;
              }

              // NOTE: must set different priorities for items, otherwise system can hide all items with the same priority (but some of them is able to be placed)
              tbb = out.addAnActionButton(anAct).setShowMode(TBItemAnActionButton.SHOWMODE_TEXT_ONLY).setModality(ms).setComponent(ob)
                .setPriority(--prio[0]);
            }
          }
        }

        if (tbb != null) {
          _setDialogLayout(tbb);
        }
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
            if (ni.getParentDesc() != null && !ni.getParentDesc().isShowImage()) {
              butt.setShowMode(TBItemAnActionButton.SHOWMODE_TEXT_ONLY);
            }
          }
        };
        addActionGroup(out, ag, customizer);

        ag.addPropertyChangeListener(new PropertyChangeListener() {
          @Override
          public void propertyChange(PropertyChangeEvent evt) {
            if (!UpdatableDefaultActionGroup.PROP_CHILDREN.equals(evt.getPropertyName())) {
              return;
            }

            final AnAction[] prevArr = (AnAction[])evt.getOldValue();
            final AnAction[] currArr = (AnAction[])evt.getNewValue();

            final List<AnAction> prev = new ArrayList<>();
            for (AnAction act : prevArr) {
              _collectActions(act, prev);
            }
            final List<AnAction> curr = new ArrayList<>();
            for (AnAction act : currArr) {
              _collectActions(act, curr);
            }

            if (prev != null && !prev.isEmpty()) {
              if (curr != null && !curr.isEmpty()) {
                // System.out.printf("replace %d old actions with %d new inside: %s\n", prev.size(), curr.size(), out);
                if (curr.size() != prev.size()) {
                  out.getItemsContainer().remove(tbi -> DIALOG_ACTIONS_CONTEXT.equals(tbi.myOptionalContextName));
                  // System.out.printf("mismatch sizes of prev %d and cur %d actions inside: %s\n", prev.size(), curr.size(), out);
                  addActionGroup(out, ag, customizer);
                }
                else {
                  out.getItemsContainer().forEachDeep(tbi -> {
                    if (!(tbi instanceof TBItemAnActionButton)) {
                      return;
                    }

                    final TBItemAnActionButton butt = (TBItemAnActionButton)tbi;
                    final int prevIndex = prev.indexOf(butt.getAnAction());
                    if (prevIndex == -1) {
                      return;
                    }

                    final AnAction newAct = curr.get(prevIndex);
                    // System.out.printf("prev act '%s', new act '%s'\n", getActionId(butt.getAnAction()), getActionId(newAct));
                    butt.setAnAction(newAct);
                  });
                }
              }
              else {
                out.getItemsContainer().remove(tbi -> tbi instanceof TBItemAnActionButton &&
                                                      prev.contains(((TBItemAnActionButton)tbi).getAnAction()));
              }
            }
            else {
              if (curr == null || curr.isEmpty()) // nothing to do
              {
                return;
              }

              // System.out.println("prev actions is null, add all new actions into: " + out);
              addActionGroup(out, ag, customizer);
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
        if (anAct == null) {
          continue;
        }
        final TBItemAnActionButton tbb = out.createActionButton(anAct);
        tbb.setShowMode(TBItemAnActionButton.SHOWMODE_TEXT_ONLY).setModality(ms).setComponent(jb);
        if (desc.isMainGroup()) {
          group.addItem(tbb);
        }
        else {
          out.getItemsContainer().addItem(tbb, gr);
          tbb.setPriority(--prio[0]);
        }

        boolean isDefault = desc.isDefault();
        if (!isDefault) {
          // check other properties
          isDefault = jb.getAction() != null ? jb.getAction().getValue(DialogWrapper.DEFAULT_ACTION) != null : jb.isDefaultButton();
        }
        if (isDefault) {
          tbb.setColored(true);
        }
        _setDialogLayout(tbb);
      }

      out.selectVisibleItemsToShow();
      out.setPrincipal(gr);
    }
  }

  // creates releaseOnClose touchbar
  static TouchBar createScrubberBarFromPopup(@NotNull ListPopupImpl listPopup) {
    final TouchBar result = new TouchBar("popup_scrubber_bar" + listPopup, true, false, true, null, null);

    final ModalityState ms = LaterInvocator.getCurrentModalityState();

    final TBItemScrubber scrub = result.addScrubber();
    final @NotNull ListPopupStep<Object> listPopupStep = listPopup.getListStep();
    final @NotNull List<Object> stepValues = listPopupStep.getValues();
    final List<Integer> disabledItems = new ArrayList<>();
    int currIndex = 0;
    final Map<Object, Integer> obj2index = new HashMap<>();
    for (Object obj : stepValues) {
      final Icon ic = listPopupStep.getIconFor(obj);
      String txt = listPopupStep.getTextFor(obj);

      if (listPopupStep.isMnemonicsNavigationEnabled()) {
        final MnemonicNavigationFilter<Object> filter = listPopupStep.getMnemonicNavigationFilter();
        final int pos = filter == null ? -1 : filter.getMnemonicPos(obj);
        if (pos != -1) {
          txt = txt.substring(0, pos) + txt.substring(pos + 1);
        }
      }

      final Runnable edtAction = () -> {
        if (obj != null) {
          listPopup.getList().setSelectedValue(obj, false);
        }
        else {
          listPopup.getList().setSelectedIndex(stepValues.indexOf(obj));
        }
        listPopup.handleSelect(true);
      };

      final Runnable action = () -> {
        ApplicationManager.getApplication().invokeLater(edtAction, ms);
      };
      scrub.addItem(ic, txt, action);
      if (!listPopupStep.isSelectable(obj)) {
        disabledItems.add(currIndex);
      }
      obj2index.put(obj, currIndex);
      ++currIndex;
    }

    final ListModel model = listPopup.getList().getModel();
    model.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {}

      @Override
      public void intervalRemoved(ListDataEvent e) {}

      @Override
      public void contentsChanged(ListDataEvent e) {
        final List<Integer> visibleIndices = new ArrayList<>();
        for (int c = 0; c < model.getSize(); ++c) {
          final Object visibleItem = model.getElementAt(c);
          final Integer itemId = obj2index.get(visibleItem);
          if (itemId != null) {
            visibleIndices.add(itemId);
          }
        }

        scrub.showItems(visibleIndices, true, true);
      }
    });

    result.selectVisibleItemsToShow();
    scrub.enableItems(disabledItems, false);
    return result;
  }

  // creates releaseOnClose touchbar
  static TouchBar createStopRunningBar(List<? extends Pair<RunContentDescriptor, Runnable>> stoppableDescriptors) {
    final TouchBar tb = new TouchBar("select_running_configuration_to_stop", true, true, true, null, null);
    tb.addButton().setText("Stop All").setAction(() -> stoppableDescriptors.forEach((pair) -> pair.second.run()), true, null);
    final TBItemScrubber stopScrubber = tb.addScrubber();
    for (Pair<RunContentDescriptor, Runnable> sd : stoppableDescriptors) {
      stopScrubber.addItem(sd.first.getIcon(), sd.first.getDisplayName(), sd.second);
    }
    tb.selectVisibleItemsToShow();
    return tb;
  }

  static class GroupVisitor implements INodeInfo, ActionGroupVisitor {
    private final @NotNull TouchBar myOut;
    private final @Nullable String myFilterByPrefix;
    private final @Nullable Customizer myCustomizer;
    private final boolean myAllowSkipSlowUpdates;

    private int mySeparatorCounter = 0;
    private final LinkedList<InternalNode> myNodePath = new LinkedList<>();
    private final List<InternalNode> myVisitedNodes = new ArrayList<>();
    private InternalNodeWithContainer myRoot;

    private final Map<AnAction, Long> myAct2StartUpdateNs = new ConcurrentHashMap<>();

    private final @Nullable TouchBarStats myStats;

    GroupVisitor(@NotNull TouchBar out,
                 @Nullable String filterByPrefix,
                 @Nullable Customizer customizer,
                 @Nullable TouchBarStats stats,
                 boolean allowSkipSlowUpdates) {
      this.myOut = out;
      this.myFilterByPrefix = filterByPrefix;
      this.myCustomizer = customizer;
      this.myStats = stats;
      myAllowSkipSlowUpdates = allowSkipSlowUpdates;
    }

    @Override
    public Component getCustomComponent(@NotNull AnAction action) {
      final TouchbarDataKeys.ActionDesc actionDesc =
        action.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
      return actionDesc != null ? actionDesc.getContextComponent() : null;
    }

    private @Nullable TouchBarStats.AnActionStats getActionStats(@NotNull String actId) {
      return myStats == null ? null : myStats.getActionStats(actId);
    }

    @Override
    public void begin() {
      myOut.softClear();
    }

    @Override
    public boolean enterNode(@NotNull ActionGroup groupNode) {
      final @NotNull String groupId = getActionId(groupNode);
      if (myFilterByPrefix != null && groupId.startsWith(myFilterByPrefix)) {
        return false;
      }

      if (myRoot == null) {
        myRoot = new InternalNodeWithContainer(myOut.getItemsContainer(), groupNode, groupId, null, getActionStats(groupId));
      }

      final TouchbarDataKeys.ActionDesc groupDesc =
        groupNode.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
      InternalNode node = null;
      if (groupDesc != null && groupDesc.isMainGroup()) {
        @NotNull InternalNodeWithContainer currNode = _getCurrentNodeContainer();
        final TBItemGroup group = myOut.createGroup();
        currNode.container.addItem(group);
        myOut.setPrincipal(group);
        node = new InternalNodeWithContainer(group.getContainer(), groupNode, groupId, groupDesc, getActionStats(groupId));
      }

      if (node == null) {
        node = new InternalNode(groupNode, groupId, groupDesc, getActionStats(groupId));
      }
      myNodePath.add(node);
      myVisitedNodes.add(node);
      return true;
    }

    @Override
    public void visitLeaf(@NotNull AnAction act) {
      @NotNull InternalNodeWithContainer currNode = _getCurrentNodeContainer();

      if (act instanceof Separator) {
        final Separator sep = (Separator)act;
        int increment = 1;
        if (sep.getText() != null) {
          if (sep.getText().equals(ourSmallSeparatorText)) {
            currNode.container.addSpacing(false);
          }
          else if (sep.getText().equals(ourLargeSeparatorText)) {
            currNode.container.addSpacing(true);
          }
          else if (sep.getText().equals(ourFlexibleSeparatorText)) {
            currNode.container.addFlexibleSpacing();
          }
        }
        else {
          mySeparatorCounter += increment;
        }

        return;
      } // separator

      if (mySeparatorCounter > 0) {
        if (mySeparatorCounter == 1) {
          currNode.container.addSpacing(false);
        }
        else if (mySeparatorCounter == 2) {
          currNode.container.addSpacing(true);
        }
        else {
          currNode.container.addFlexibleSpacing();
        }
        mySeparatorCounter = 0;
      }

      final TBItemAnActionButton butt = myOut.createActionButton(act);
      currNode.container.addItem(butt);

      if (myCustomizer != null) {
        myCustomizer.process(this, butt);
      }
    }

    @Override
    public void leaveNode() {
      final InternalNode leaved = myNodePath.removeLast();
      leaved.leaveTimeNs = System.nanoTime();
    }

    @Override
    public boolean beginUpdate(@NotNull AnAction action, AnActionEvent e) {
      if (SKIP_UPDATE_SLOW_ACTIONS && myAllowSkipSlowUpdates && isSlowUpdateAction(action)) {
        // make such action always enabled and visible
        e.getPresentation().setEnabledAndVisible(true);
        if (action instanceof Toggleable) {
          Toggleable.setSelected(e.getPresentation(), false);
        }
        return false;
      }
      myAct2StartUpdateNs.put(action, System.nanoTime());
      return true;
    }

    @Override
    public void endUpdate(@NotNull AnAction action) {
      // check whether update is too slow
      final long updateDurationNs = System.nanoTime() - myAct2StartUpdateNs.getOrDefault(action, 0L);
      final boolean isEDT = ApplicationManager.getApplication().isDispatchThread();
      if (isEDT && SKIP_UPDATE_SLOW_ACTIONS && myAllowSkipSlowUpdates && updateDurationNs > 30 * 1000000L) { // 30 ms threshold
        // disable update for this action
        addSlowUpdateAction(action);
      }

      if (myStats != null) {
        final TouchBarStats.AnActionStats stats = myStats.getActionStats(action);
        stats.onUpdate(updateDurationNs);
      }
    }

    @Override
    public boolean isParentCompact() { return !myNodePath.isEmpty() && myNodePath.getLast().group instanceof CompactActionGroup; }

    @Override
    public String getParentGroupID() {
      if (myNodePath.isEmpty()) {
        return null;
      }
      return myNodePath.getLast().groupID;
    }

    @Override
    public TouchbarDataKeys.ActionDesc getParentDesc() {
      if (myNodePath.isEmpty()) {
        return null;
      }
      return myNodePath.getLast().groupDesc;
    }

    private @NotNull InternalNodeWithContainer _getCurrentNodeContainer() {
      if (myNodePath.isEmpty()) {
        return myRoot;
      }

      Iterator<InternalNode> di = myNodePath.descendingIterator();
      while (di.hasNext()) {
        InternalNode in = di.next();
        if (in instanceof InternalNodeWithContainer) {
          return (InternalNodeWithContainer)in;
        }
      }
      return myRoot;
    }

    private static class InternalNode {
      final @NotNull ActionGroup group;
      final @NotNull String groupID;
      final @Nullable TouchbarDataKeys.ActionDesc groupDesc;
      final @Nullable TouchBarStats.AnActionStats myActionStats;

      final long bornTimeNs;
      long leaveTimeNs;

      InternalNode(@NotNull ActionGroup group,
                   @NotNull String groupID,
                   @Nullable TouchbarDataKeys.ActionDesc groupDesc,
                   @Nullable TouchBarStats.AnActionStats stats) {
        this.group = group;
        this.groupID = groupID;
        this.groupDesc = groupDesc;
        this.myActionStats = stats;

        bornTimeNs = System.nanoTime();
      }
    }
  }

  private static final class InternalNodeWithContainer extends GroupVisitor.InternalNode {
    final @NotNull ItemsContainer container;

    InternalNodeWithContainer(@NotNull ItemsContainer container,
                              @NotNull ActionGroup group,
                              @NotNull String groupID,
                              @Nullable TouchbarDataKeys.ActionDesc groupDesc,
                              @Nullable TouchBarStats.AnActionStats stats) {
      super(group, groupID, groupDesc, stats);
      this.container = container;
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

    private TBItemAnActionButton myRunConfigurationButton;
    private List<TBItemAnActionButton> myRunnerButtons;

    Customizer(int showMode, @Nullable ModalityState modality) {
      myShowMode = showMode;
      myModality = modality;
    }

    Customizer(@Nullable TouchbarDataKeys.ActionDesc groupDesc, @Nullable ModalityState modality) {
      myShowMode = groupDesc == null || !groupDesc.isShowText()
                   ? TBItemAnActionButton.SHOWMODE_IMAGE_ONLY_IF_PRESENTED
                   : TBItemAnActionButton.SHOWMODE_IMAGE_TEXT;
      myModality = modality;
    }

    Customizer() {
      myShowMode = TBItemAnActionButton.SHOWMODE_IMAGE_ONLY_IF_PRESENTED;
      myModality = null;
    }

    void process(@NotNull INodeInfo ni, @NotNull TBItemAnActionButton butt) {
      final @NotNull String actId = getActionId(butt.getAnAction());

      final boolean isRunConfigPopover = "RunConfiguration".equals(actId);
      final boolean isOpenInTerminalAction = "Terminal.OpenInTerminal".equals(actId);
      final TouchbarDataKeys.ActionDesc pd = ni.getParentDesc();
      final int nodeMode = pd != null && pd.isShowText() ? TBItemAnActionButton.SHOWMODE_IMAGE_TEXT : myShowMode;
      final int mode = isRunConfigPopover || isOpenInTerminalAction ? TBItemAnActionButton.SHOWMODE_IMAGE_TEXT : nodeMode;
      butt.setShowMode(mode).setModality(myModality);
      if (ni.isParentCompact()) {
        butt.setHiddenWhenDisabled(true);
      }

      if (isRunConfigPopover) {
        myRunConfigurationButton = butt;
      }
      else if (butt.getAnAction() instanceof WelcomePopupAction) {
        butt.setHasArrowIcon(true);
      }
      else if ("RunnerActionsTouchbar".equals(ni.getParentGroupID()) || "Stop".equals(actId)) {
        if (myRunnerButtons == null) {
          myRunnerButtons = new ArrayList<>();
        }
        myRunnerButtons.add(butt);
      }

      final TouchbarDataKeys.ActionDesc actionDesc =
        butt.getAnAction().getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
      if (actionDesc != null && actionDesc.getContextComponent() != null) {
        butt.setComponent(actionDesc.getContextComponent());
      }
    }

    void finish() {
      if (myRunConfigurationButton != null && myRunnerButtons != null && !myRunnerButtons.isEmpty()) {
        myRunConfigurationButton.setLinkedButtons(myRunnerButtons);
      }
    }
  }

  static @NotNull String getActionId(@NotNull AnAction action) {
    String actionId =
      ActionManager.getInstance().getId(action instanceof CustomisedActionGroup ? ((CustomisedActionGroup)action).getOrigin() : action);
    return actionId == null ? action.toString() : actionId;
  }

  private static void _setDialogLayout(TBItemButton button) {
    if (button == null) {
      return;
    }
    button.setLayout(BUTTON_MIN_WIDTH_DLG, NSTLibrary.LAYOUT_FLAG_MIN_WIDTH, BUTTON_IMAGE_MARGIN, BUTTON_BORDER);
  }

  private static AnAction _createAnAction(@Nullable Action action,
                                          @NotNull JButton fromButton,
                                          boolean useTextFromAction /*for optional buttons*/) {
    final Object anAct = action == null ? null : action.getValue(OptionAction.AN_ACTION);
    if (anAct == null) {
      // LOG.warn("null AnAction in action: '" + action + "', use wrapper");
      return new DumbAwareAction() {
        {
          setEnabledInModalContext(true);
          if (useTextFromAction) {
            final Object name = action == null ? fromButton.getText() : action.getValue(Action.NAME);
            getTemplatePresentation().setText(name instanceof String ? (String)name : "");
          }
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          if (action == null) {
            fromButton.doClick();
            return;
          }
          // also can be used something like: ApplicationManager.getApplication().invokeLater(() -> jb.doClick(), ms)
          action.actionPerformed(new ActionEvent(fromButton, ActionEvent.ACTION_PERFORMED, null));
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(action == null ? fromButton.isEnabled() : action.isEnabled());
          if (!useTextFromAction) {
            e.getPresentation().setText(DialogWrapper.extractMnemonic(fromButton.getText()).second);
          }
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
    if (unorderedButtons.isEmpty()) {
      return null;
    }

    final List<TouchbarDataKeys.DlgButtonDesc> main = new ArrayList<>();
    final List<TouchbarDataKeys.DlgButtonDesc> secondary = new ArrayList<>();
    for (Map.Entry<TouchbarDataKeys.DlgButtonDesc, JButton> entry : unorderedButtons.entrySet()) {
      final TouchbarDataKeys.DlgButtonDesc jbdesc = entry.getKey();
      if (jbdesc == null) {
        continue;
      }

      if (jbdesc.isMainGroup()) {
        main.add(jbdesc);
      }
      else {
        secondary.add(jbdesc);
      }
    }

    final Comparator<TouchbarDataKeys.DlgButtonDesc> cmp = Comparator.comparingInt(TouchbarDataKeys.DlgButtonDesc::getOrder);
    main.sort(cmp);
    secondary.sort(cmp);
    return ContainerUtil.concat(secondary, main);
  }

  private static void _collectActions(@NotNull AnAction act, @NotNull List<? super AnAction> out) {
    if (act instanceof ActionGroup) {
      final ActionGroup group = (ActionGroup)act;
      final AnAction[] children = group.getChildren(null);
      for (final AnAction child : children) {
        if (child == null) {
          continue;
        }

        if (child instanceof ActionGroup) {
          final @NotNull ActionGroup childGroup = (ActionGroup)child;
          _collectActions(childGroup, out);
        }
        else {
          out.add(child);
        }
      }
    }
    else {
      out.add(act);
    }
  }

  private static final String SLOW_UPDATE_ACTIONS_KEY = "touchbar.slow.update.actions";
  private static @NotNull Set<String> ourSlowActions;

  private static void initSlowActions() {
    if (ourSlowActions != null) {
      return;
    }
    ourSlowActions = ConcurrentHashMap.newKeySet();
    if (PERSISTENT_LIST_OF_SLOW_ACTIONS) {
      final String[] muted = PropertiesComponent.getInstance().getValues(SLOW_UPDATE_ACTIONS_KEY);
      if (muted != null) {
        Collections.addAll(ourSlowActions, muted);
      }
    }
  }

  private static void saveSlowActions() {
    if (PERSISTENT_LIST_OF_SLOW_ACTIONS) {
      PropertiesComponent.getInstance().setValues(SLOW_UPDATE_ACTIONS_KEY, ArrayUtilRt.toStringArray(ourSlowActions));
    }
  }

  private static boolean isSlowUpdateAction(@NotNull String actionId) {
    initSlowActions();
    return ourSlowActions.contains(actionId);
  }

  private static boolean isSlowUpdateAction(@NotNull AnAction action) {
    final String actId = ActionManager.getInstance().getId(action);
    if (actId == null) {
      return false;
    }
    return isSlowUpdateAction(actId);
  }

  private static void addSlowUpdateAction(@NotNull AnAction action) {
    final String actId = ActionManager.getInstance().getId(action);
    if (actId == null) {
      return;
    }
    addSlowUpdateAction(actId);
  }

  private static void addSlowUpdateAction(@NotNull String actId) {
    initSlowActions();
    ourSlowActions.add(actId);
    saveSlowActions();
  }
}
