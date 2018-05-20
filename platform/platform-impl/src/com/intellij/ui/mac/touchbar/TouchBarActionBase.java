// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class TouchBarActionBase extends TouchBarProjectBase {
  private static final Logger LOG = Logger.getInstance(TouchBarActionBase.class);
  private static final String ourLargeSeparatorText = "type.big";
  private static final String ourFlexibleSeparatorText = "type.flexible";
  private static final int ourRunConfigurationPopoverWidth = 143;

  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private final TimerListener myTimerListener;

  static {
    _initExecutorsGroup();
  }

  public TouchBarActionBase(@NotNull String touchbarName, @NotNull Project project) { this(touchbarName, project, false); }

  public TouchBarActionBase(@NotNull String touchbarName, @NotNull Project project, @NotNull ActionGroup customizedGroup, boolean replaceEsc) {
    this(touchbarName, project, replaceEsc);

    final String groupId = _getActionId(customizedGroup);
    if (groupId == null) {
      LOG.error("unregistered group: " + customizedGroup);
      return;
    }
    addActionGroupButtons(customizedGroup, null, TBItemAnActionButton.SHOWMODE_IMAGE_ONLY_IF_PRESENTED, nodeId -> nodeId.contains(groupId + "_"), null);
  }

  public TouchBarActionBase(@NotNull String touchbarName, @NotNull Project project, boolean replaceEsc) {
    super(touchbarName, project, replaceEsc);

    myTimerListener = new TimerListener() {
      @Override
      public ModalityState getModalityState() { return ModalityState.current(); }
      @Override
      public void run() { updateActionItems(); }
    };
  }

  @Override
  public void release() {
    super.release();
    ActionManager.getInstance().removeTransparentTimerListener(myTimerListener);
  }

  @Override
  public void onBeforeShow() {
    updateActionItems();
    ActionManager.getInstance().addTransparentTimerListener(500/*delay param doesn't affect anything*/, myTimerListener);
  }
  @Override
  public void onHide() { ActionManager.getInstance().removeTransparentTimerListener(myTimerListener); }

  private TBItemAnActionButton _addAnActionButton(@NotNull AnAction act, boolean hiddenWhenDisabled, int showMode, ModalityState modality) {
    final String uid = String.format("%s.anActionButton.%d.%s", myName, myCounter++, ActionManager.getInstance().getId(act));
    final TBItemAnActionButton butt = new TBItemAnActionButton(uid, act, hiddenWhenDisabled, showMode, modality);
    myItems.add(butt);
    return butt;
  }

  public void setComponent(Component component/*for DataContext*/) {
    myItems.forEach(item -> {
      if (item instanceof TBItemAnActionButton)
        ((TBItemAnActionButton)item).setComponent(component);
    });
  }

  public void addActionGroupButtons(ActionGroup actionGroup, ModalityState modality, int showMode) { addActionGroupButtons(actionGroup, modality, showMode, null, null); }

  public void addActionGroupButtons(ActionGroup actionGroup, ModalityState modality, int showMode, INodeFilter filter, ICustomizer customizer) {
    _traverse(actionGroup, new ILeafVisitor() {
      private int mySeparatorCounter = 0;

      @Override
      public void visit(AnAction act) {
        if (act instanceof Separator) {
          final Separator sep = (Separator)act;
          int increment = 1;
          if (sep.getText() != null) {
            if (sep.getText().equals(ourLargeSeparatorText)) increment = 2;
            if (sep.getText().equals(ourFlexibleSeparatorText)) increment = 3;
          }
          mySeparatorCounter += increment;
          return;
        }
        if (mySeparatorCounter > 0) {
          if (mySeparatorCounter == 1)            addSpacing(false);
          else if (mySeparatorCounter == 2)       addSpacing(true);
          else                                    addFlexibleSpacing();

          mySeparatorCounter = 0;
        }

        final String actId = _getActionId(act);
        // if (actId == null || actId.isEmpty())   System.out.println("unregistered action: " + act);

        final boolean isRunConfigPopover = actId != null && actId.contains("RunConfiguration");
        final int mode = isRunConfigPopover ? TBItemAnActionButton.SHOWMODE_IMAGE_TEXT : showMode;
        final TBItemAnActionButton butt = _addAnActionButton(act, false, mode, modality);

        if (isRunConfigPopover)
          butt.setWidth(ourRunConfigurationPopoverWidth);

        if (customizer != null)
          customizer.customize(butt);
      }
    }, filter);
  }

  void updateActionItems() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    boolean layoutChanged = false;
    for (TBItem tbitem: myItems) {
      if (!(tbitem instanceof TBItemAnActionButton))
        continue;

      final TBItemAnActionButton item = (TBItemAnActionButton)tbitem;
      final Presentation presentation = myPresentationFactory.getPresentation(item.getAnAction());

      try {
        item.updateAnAction(presentation);
      } catch (IndexNotReadyException e1) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
      }

      if (item.isAutoVisibility()) {
        final boolean itemVisibilityChanged = item.updateVisibility(presentation);
        if (itemVisibilityChanged)
          layoutChanged = true;
      }
      item.updateView(presentation);
    }

    if (layoutChanged)
      selectVisibleItemsToShow();
  }

  private static String _getActionId(AnAction act) { return ActionManager.getInstance().getId(act instanceof CustomisedActionGroup ? ((CustomisedActionGroup)act).getOrigin() : act); }

  public static ActionGroup getCustomizedGroup(@NotNull String barId) {
    final ActionGroup actGroup = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_TOUCHBAR);
    final AnAction[] kids = actGroup.getChildren(null);
    final String childGroupId = barId.startsWith(IdeActions.GROUP_TOUCHBAR) ? barId : IdeActions.GROUP_TOUCHBAR + barId;

    for (AnAction act: kids) {
      if (!(act instanceof ActionGroup))
        continue;
      final String gid = _getActionId(act);
      if (gid == null || gid.isEmpty()) {
        LOG.error("unregistered ActionGroup: " + act);
        continue;
      }
      if (gid.equals(childGroupId))
        return (ActionGroup)act;
    }

    return null;
  }

  public static Map<String, ActionGroup> getAltLayouts(@NotNull ActionGroup context) {
    final String ctxId = _getActionId(context);
    if (ctxId == null || ctxId.isEmpty()) {
      LOG.error("unregistered ActionGroup: " + context);
      return null;
    }

    Map<String, ActionGroup> result = new HashMap<>();
    final AnAction[] kids = context.getChildren(null);
    for (AnAction act: kids) {
      if (!(act instanceof ActionGroup))
        continue;
      final String gid = _getActionId(act);
      if (gid == null || gid.isEmpty()) {
        LOG.error("unregistered ActionGroup: " + act);
        continue;
      }
      if (gid.startsWith(ctxId + "_"))
        result.put(gid.substring(ctxId.length() + 1), (ActionGroup)act);
    }

    return result;
  }

  private static @Nullable AnAction _getActionById(String actId) {
    final AnAction act = ActionManager.getInstance().getAction(actId);
    if (act == null)
      LOG.error("can't find action by id: " + actId);

    return act;
  }

  protected interface INodeFilter {
    boolean skip(String nodeId);
  }
  protected interface ICustomizer {
    void customize(TBItem item);
  }
  private interface ILeafVisitor {
    void visit(AnAction leaf);
  }

  private static final String RUNNERS_GROUP_TOUCHBAR = "RunnerActionsTouchbar";

  private static void _traverse(@NotNull ActionGroup group, ILeafVisitor visitor, INodeFilter filter) {
    String groupId = _getActionId(group);
    if (groupId == null) groupId = "unregistered";

    final AnAction[] children = group.getChildren(null);
    for (int i = 0; i < children.length; i++) {
      AnAction child = children[i];
      if (child == null) {
        LOG.error(String.format("action is null: i=%d, group='%s', group id='%s'",i, group.toString(), groupId));
        continue;
      }

      String childId = _getActionId(child);
      if (childId == null) childId = "unregistered";

      if (child instanceof ActionGroup) {
        ActionGroup actionGroup = (ActionGroup)child;
        if (actionGroup.isPopup()) {
          LOG.error(String.format("children with isPopup=true aren't supported now: i=%d, childId='%s', group='%s', group id='%s'", i, childId, group.toString(), groupId));
          continue;
        }
        if (filter != null && filter.skip(childId)) {
          // System.out.printf("filter child group: i=%d, childId='%s', group='%s', group id='%s'\n", i, childId, group.toString(), groupId);
          continue;
        }
        _traverse((ActionGroup)child, visitor, filter);
      } else
        visitor.visit(child);
    }
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
