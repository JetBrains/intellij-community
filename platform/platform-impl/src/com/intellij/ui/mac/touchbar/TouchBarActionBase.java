// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class TouchBarActionBase extends TouchBarProjectBase implements ExecutionListener {
  private static final Logger LOG = Logger.getInstance(TouchBarActionBase.class);

  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private final TimerListener myTimerListener;
  private final Component myComponent;

  public TouchBarActionBase(@NotNull String touchbarName, @NotNull Project project, Component component) { this(touchbarName, project, component, false); }

  public TouchBarActionBase(@NotNull String touchbarName, @NotNull Project project, Component component, boolean replaceEsc) {
    super(touchbarName, project, replaceEsc);

    myComponent = component;
    myTimerListener = new TimerListener() {
      @Override
      public ModalityState getModalityState() { return ModalityState.current(); }
      @Override
      public void run() { _updateActionItems(); }
    };

    final MessageBus mb = project.getMessageBus();
    mb.connect().subscribe(ExecutionManager.EXECUTION_TOPIC, this);
  }

  @Override
  public void release() {
    super.release();
    ActionManager.getInstance().removeTransparentTimerListener(myTimerListener);
  }

  @Override
  public void onBeforeShow() {
    _updateActionItems();
    ActionManager.getInstance().addTransparentTimerListener(500/*delay param doesn't affect anything*/, myTimerListener);
  }
  @Override
  public void onHide() { ActionManager.getInstance().removeTransparentTimerListener(myTimerListener); }

  TBItemAnActionButton addAnActionButton(String actId) {
    final AnAction act = _getActionById(actId);
    if (act == null)
      return null;

    return _addAnActionButton(act, true, TBItemAnActionButton.SHOWMODE_IMAGE_ONLY, myComponent, null);
  }

  TBItemAnActionButton addAnActionButton(String actId, boolean hiddenWhenDisabled) {
    final AnAction act = _getActionById(actId);
    if (act == null)
      return null;

    return _addAnActionButton(act, hiddenWhenDisabled, TBItemAnActionButton.SHOWMODE_IMAGE_ONLY, myComponent, null);
  }

  TBItemAnActionButton addAnActionButton(String actId, boolean hiddenWhenDisabled, int showMode) {
    final AnAction act = _getActionById(actId);
    if (act == null)
      return null;

    return _addAnActionButton(act, hiddenWhenDisabled, showMode, myComponent, null);
  }

  private TBItemAnActionButton _addAnActionButton(@NotNull AnAction act, boolean hiddenWhenDisabled, int showMode, Component component, ModalityState modality) {
    final String uid = String.format("%s.anActionButton.%d.%s", myName, myCounter++, ActionManager.getInstance().getId(act));
    final TBItemAnActionButton butt = new TBItemAnActionButton(uid, act, hiddenWhenDisabled, showMode, component, modality);
    myItems.add(butt);
    return butt;
  }

  public void addActionGroupButtons(ActionGroup actionGroup, JBOptionButton forCtx, ModalityState modality) {
    final DataContext dctx = DataManager.getInstance().getDataContext(forCtx);
    List<AnAction> visibleActions = ContainerUtil.newArrayListWithCapacity(10);
    Utils.expandActionGroup(false, actionGroup, visibleActions, myPresentationFactory, dctx, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    for (AnAction act: visibleActions) {
      if (act == null || act instanceof Separator)
        continue;

      _addAnActionButton(act, false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY, forCtx, modality);
    }
  }

  @Override
  public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
    _updateActionItems();
  }
  @Override
  public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
    ApplicationManager.getApplication().invokeLater(()->{
      _updateActionItems();
    });
  }

  protected void _updateActionItems() {
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

  private static @Nullable AnAction _getActionById(String actId) {
    final AnAction act = ActionManager.getInstance().getAction(actId);
    if (act == null)
      LOG.error("can't find action by id: " + actId);

    return act;
  }
}
