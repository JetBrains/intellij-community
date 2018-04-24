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
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.awt.*;

public class TouchBarActionBase extends TouchBarProjectBase implements ExecutionListener {
  private static final Logger LOG = Logger.getInstance(TouchBarActionBase.class);

  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private final TimerListener myTimerListener;

  public TouchBarActionBase(@NotNull String touchbarName, @NotNull Project project) {
    super(touchbarName, project);

    myTimerListener = new TimerListener() {
      @Override
      public ModalityState getModalityState() { return ModalityState.current(); }
      @Override
      public void run() { _updateActionItems(); }
    };
    ActionManager.getInstance().addTransparentTimerListener(500, myTimerListener); // NOTE: delay param doesn't affect anything

    final MessageBus mb = project.getMessageBus();
    mb.connect().subscribe(ExecutionManager.EXECUTION_TOPIC, this);
  }

  @Override
  public void release() {
    super.release();
    ActionManager.getInstance().removeTimerListener(myTimerListener);
  }

  TBItemAnActionButton addAnActionButton(String actId) {
    return addAnActionButton(ActionManager.getInstance().getAction(actId), true, TBItemAnActionButton.SHOWMODE_IMAGE_ONLY, null, null);
  }

  TBItemAnActionButton addAnActionButton(String actId, boolean hiddenWhenDisabled) {
    final AnAction act = ActionManager.getInstance().getAction(actId);
    if (act == null) {
      LOG.error("can't find action by id: " + actId);
      return null;
    }
    return addAnActionButton(act, hiddenWhenDisabled, TBItemAnActionButton.SHOWMODE_IMAGE_ONLY, null, null);
  }

  TBItemAnActionButton addAnActionButton(AnAction act, boolean hiddenWhenDisabled, int showMode) {
    return addAnActionButton(act, hiddenWhenDisabled, showMode, null, null);
  }

  TBItemAnActionButton addAnActionButton(AnAction act, boolean hiddenWhenDisabled, int showMode, Component component, ModalityState modality) {
    if (act == null) {
      LOG.error("can't create action-button with null action");
      return null;
    }

    final String uid = String.format("%s.anActionButton.%d", myName, myCounter++);
    final TBItemAnActionButton butt = new TBItemAnActionButton(uid, act, hiddenWhenDisabled, showMode, component, modality);
    myItems.add(butt);
    return butt;
  }

  public void addActionGroupButtons(ActionGroup actionGroup, JBOptionButton forCtx, ModalityState modality) {
    final DataContext dctx = DataManager.getInstance().getDataContext(forCtx);
    List<AnAction> visibleActions = ContainerUtil.newArrayListWithCapacity(10);
    Utils.expandActionGroup(false, actionGroup, visibleActions, myPresentationFactory, dctx, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    for (AnAction act: visibleActions) {
      if (act instanceof Separator)
        continue;

      addAnActionButton(act, false, TBItemAnActionButton.SHOWMODE_TEXT_ONLY, forCtx, modality);
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
      item.updateAnAction(presentation);

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
}
