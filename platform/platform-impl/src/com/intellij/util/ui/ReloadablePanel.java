/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public abstract class ReloadablePanel<T> {
  public interface DataProvider<T> {

    /**
     * returns init cached values
     */
    @Nullable
    Set<T> getCachedValues();

    /**
     * After getting values method must call #onUpdateValues or #onValuesUpdateError
     */
    void updateValuesAsynchronously();
  }

  private static final String CONTROL_PLACE = "UI.Configuration.Component.Reload.Panel";

  private final AsyncProcessIcon myLoadingVersionIcon = new AsyncProcessIcon("Getting possible values");

  private final JLabel myErrorMessage = new JLabel();
  @Nullable
  private volatile DataProvider<T> myDataProvider;
  @Nullable
  private volatile UpdateStatus myUpdateStatus;

  protected JPanel myActionPanel;
  public ReloadablePanel() {
    myErrorMessage.setForeground(JBColor.RED);

    createActionPanel();
    fillActionPanel();
  }

  private void createActionPanel() {
    myActionPanel = new JPanel(new CardLayout());
  }

  public final void setDataProvider(@NotNull DataProvider<T> dataProvider) {
    myDataProvider = dataProvider;

    Set<T> cachedValues = dataProvider.getCachedValues();
    if (cachedValues != null) {
      onUpdateValues(cachedValues);
    }
  }

  protected abstract void doUpdateValues(@NotNull Set<T> values);

  public abstract T getSelectedValue();

  @NotNull
  public final JLabel getErrorComponent() {
    return myErrorMessage;
  }

  public final boolean isBackgroundJobRunning() {
    return myUpdateStatus == UpdateStatus.UPDATING;
  }

  public final void onUpdateValues(@NotNull Set<T> values) {
    changeUpdateStatus(UpdateStatus.IDLE);
    doUpdateValues(values);
  }

  public final void reloadValuesInBackground() {
    if (myUpdateStatus == UpdateStatus.UPDATING) return;
    changeUpdateStatus(UpdateStatus.UPDATING);
    myErrorMessage.setText(null);
    DataProvider<T> provider = myDataProvider;
    assert provider != null;
    provider.updateValuesAsynchronously();
  }

  private void changeUpdateStatus(@NotNull UpdateStatus status) {
    CardLayout cardLayout = (CardLayout)myActionPanel.getLayout();
    cardLayout.show(myActionPanel, status.name());
    if (status == UpdateStatus.UPDATING) {
      myLoadingVersionIcon.resume();
    }
    else {
      myLoadingVersionIcon.suspend();
    }
    myUpdateStatus = status;
  }

  protected void fillActionPanel() {
    myActionPanel.add(createReloadButtonPanel(), UpdateStatus.IDLE.name());
    myActionPanel.add(createReloadInProgressPanel(), UpdateStatus.UPDATING.name());
    changeUpdateStatus(UpdateStatus.IDLE);
  }

  public final void onValuesUpdateError(@NotNull final String errorMessage) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (getSelectedValue() == null) {
          myErrorMessage.setText(errorMessage);
        }
        changeUpdateStatus(UpdateStatus.IDLE);
      }
    });
  }


  @NotNull
  private JPanel createReloadButtonPanel() {
    ReloadAction reloadAction = new ReloadAction();
    ActionButton reloadButton = new ActionButton(
      reloadAction,
      reloadAction.getTemplatePresentation().clone(),
      CONTROL_PLACE,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
    );
    JPanel panel = new JPanel(new BorderLayout(0, 0));
    panel.add(reloadButton, BorderLayout.WEST);
    return panel;
  }

  @NotNull
  public abstract JPanel getMainPanel();

  @NotNull
  JPanel getActionPanel(){
    return myActionPanel;
  }

  @NotNull
  private JPanel createReloadInProgressPanel() {
    JPanel panel = new JPanel();
    panel.add(myLoadingVersionIcon);
    return panel;
  }

  private final class ReloadAction extends AnAction {

    private ReloadAction() {
      super("Reload list", null, AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      reloadValuesInBackground();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }
  }

  private enum UpdateStatus {
    UPDATING, IDLE
  }
}
