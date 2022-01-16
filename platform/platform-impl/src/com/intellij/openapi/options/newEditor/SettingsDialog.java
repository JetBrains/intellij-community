// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.CommonBundle;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.SearchTextField.FindAction;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_FIND;

public class SettingsDialog extends DialogWrapper implements DataProvider {
  public static final String DIMENSION_KEY = "SettingsEditor";

  private final String myDimensionServiceKey;
  private final AbstractEditor myEditor;
  private final boolean myApplyButtonNeeded;
  private final boolean myResetButtonNeeded;
  private final JLabel myHintLabel = new JLabel();

  public SettingsDialog(Project project, String key, @NotNull Configurable configurable, boolean showApplyButton, boolean showResetButton) {
    super(project, true);
    myDimensionServiceKey = key;
    myEditor = new SingleSettingEditor(myDisposable, configurable);
    myApplyButtonNeeded = showApplyButton;
    myResetButtonNeeded = showResetButton;
    init(configurable, project);
  }

  public SettingsDialog(@NotNull Component parent, String key, @NotNull Configurable configurable, boolean showApplyButton, boolean showResetButton) {
    super(parent, true);
    myDimensionServiceKey = key;
    myEditor = new SingleSettingEditor(myDisposable, configurable);
    myApplyButtonNeeded = showApplyButton;
    myResetButtonNeeded = showResetButton;
    init(configurable, null);
  }

  public SettingsDialog(@NotNull Project project, @NotNull List<? extends ConfigurableGroup> groups, @Nullable Configurable configurable, @Nullable String filter) {
    super(project, true);
    myDimensionServiceKey = DIMENSION_KEY;
    myEditor = new SettingsEditor(myDisposable, project, groups, configurable, filter, this::treeViewFactory);
    myApplyButtonNeeded = true;
    myResetButtonNeeded = false;
    init(null, project);
  }

  public SettingsDialog(@NotNull Project project,
                        @Nullable Component parentComponent,
                        @NotNull List<? extends ConfigurableGroup> groups,
                        @Nullable Configurable configurable,
                        @Nullable String filter) {
    super(project, parentComponent, true, IdeModalityType.IDE);
    myDimensionServiceKey = DIMENSION_KEY;
    myEditor = new SettingsEditor(myDisposable, project, groups, configurable, filter, this::treeViewFactory);
    myApplyButtonNeeded = true;
    myResetButtonNeeded = false;
    init(null, project);
  }

  protected @NotNull SettingsTreeView treeViewFactory(@NotNull SettingsFilter filter, @NotNull List<? extends ConfigurableGroup> groups) {
    return new SettingsTreeView(filter, groups);
  }

  private void init(@Nullable Configurable configurable, @Nullable Project project) {
    String name = configurable == null ? null : configurable.getDisplayName();
    String hint = project != null && project.isDefault() ? IdeUICustomization.getInstance().projectMessage("template.settings.hint") : null;
    myHintLabel.setText(hint);
    setTitle(name == null ? CommonBundle.settingsTitle() : name.replace('\n', ' '));

    ShortcutSet set = getFindActionShortcutSet();
    if (set != null) {
      new FindAction().registerCustomShortcutSet(set, getRootPane(), myDisposable);
    }

    init();
    if (configurable == null) {
      JRootPane rootPane = getPeer().getRootPane();
      if (rootPane != null) {
        rootPane.setMinimumSize(new JBDimension(900, 700));
      }
    }
  }

  @Override
  protected void setHelpTooltip(@NotNull JButton helpButton) {
    //noinspection SpellCheckingInspection
    if (Registry.is("ide.helptooltip.enabled")) {
      new HelpTooltip().setDescription(ActionsBundle.actionDescription("HelpTopics")).installOn(helpButton);
    }
    else {
      super.setHelpTooltip(helpButton);
    }
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (myEditor instanceof DataProvider) {
      DataProvider provider = (DataProvider)myEditor;
      return provider.getData(dataId);
    }
    return null;
  }

  @Override
  protected String getDimensionServiceKey() {
    return myDimensionServiceKey;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditor.getPreferredFocusedComponent();
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myEditor;
  }

  @Override
  protected @Nullable JPanel createSouthAdditionalPanel() {
    JPanel panel = new NonOpaquePanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.emptyLeft(10));
    panel.add(myHintLabel);
    myHintLabel.setEnabled(false);
    return panel;
  }

  @SuppressWarnings("unused") // used in Rider
  protected void tryAddOptionsListener(OptionsEditorColleague colleague) {
    if (myEditor instanceof SettingsEditor) {
      ((SettingsEditor)myEditor).addOptionsListener(colleague);
    }
  }

  @Override
  protected Action @NotNull [] createActions() {
    ArrayList<Action> actions = new ArrayList<>();
    actions.add(getOKAction());
    actions.add(getCancelAction());
    Action apply = myEditor.getApplyAction();
    if (apply != null && myApplyButtonNeeded) {
      actions.add(apply);
    }
    Action reset = myEditor.getResetAction();
    if (reset != null && myResetButtonNeeded) {
      actions.add(reset);
    }
    if (getHelpId() != null) {
      actions.add(getHelpAction());
    }
    return actions.toArray(new Action[0]);
  }

  @Override
  protected @Nullable String getHelpId() {
    return myEditor.getHelpTopic();
  }

  @Override
  public void doOKAction() {
    applyAndClose(true);
  }

  public void applyAndClose(boolean scheduleSave) {
    Window window = getWindow();
    if (window != null) {
      UIUtil.stopFocusedEditing(window);
    }
    if (myEditor.apply()) {
      if (scheduleSave) {
        SaveAndSyncHandler.getInstance().scheduleSave(new SaveAndSyncHandler.SaveTask(null, /* forceSavingAllSettings = */ true));
      }
      super.doOKAction();
    }
  }

  @Override
  public void doCancelAction(AWTEvent source) {
    if (source instanceof KeyEvent || source instanceof ActionEvent) {
      if (!myEditor.cancel(source)) {
        return;
      }
    }
    super.doCancelAction(source);
  }

  static @Nullable ShortcutSet getFindActionShortcutSet() {
    AnAction action = ActionManager.getInstance().getAction(ACTION_FIND);
    return action == null ? null : action.getShortcutSet();
  }
}
