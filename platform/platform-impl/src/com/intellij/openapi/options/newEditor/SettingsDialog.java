// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.SearchTextField.FindAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_FIND;

public class SettingsDialog extends DialogWrapper implements DataProvider {
  public static final String DIMENSION_KEY = "SettingsEditor";

  private final String myDimensionServiceKey;
  private final AbstractEditor myEditor;
  private final boolean myApplyButtonNeeded;
  private boolean myResetButtonNeeded;

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

  public SettingsDialog(@NotNull Project project, @NotNull ConfigurableGroup[] groups, Configurable configurable, String filter) {
    super(project, true);
    myDimensionServiceKey = DIMENSION_KEY;
    myEditor = new SettingsEditor(myDisposable, project, groups, configurable, filter, this::treeViewFactory);
    myApplyButtonNeeded = true;
    init(null, project);
  }

  protected SettingsTreeView treeViewFactory(SettingsFilter filter, ConfigurableGroup[] groups) {
    return new SettingsTreeView(filter, groups);
  }

  @Override
  public void show() {
    TransactionGuard.getInstance().submitTransactionAndWait(() -> super.show());
  }


  private void init(Configurable configurable, @Nullable Project project) {
    String name = configurable == null ? null : configurable.getDisplayName();
    String title = CommonBundle.settingsTitle();
    if (project != null && project.isDefault()) {
      title = OptionsBundle.message("title.for.new.projects",
                                    title, StringUtil.capitalize(IdeUICustomization.getInstance().getProjectConceptName()));
    }
    setTitle(name == null ? title : name.replace('\n', ' '));
    ShortcutSet set = getFindActionShortcutSet();
    if (set != null) new FindAction().registerCustomShortcutSet(set, getRootPane(), myDisposable);
    init();
  }

  @Override
  public Object getData(@NonNls String dataId) {
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
  public boolean isTypeAheadEnabled() {
    return true;
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myEditor;
  }

  protected void tryAddOptionsListener(OptionsEditorColleague colleague) {
    if (myEditor instanceof SettingsEditor) {
      ((SettingsEditor) myEditor).addOptionsListener(colleague);
    }
  }

  @NotNull
  @Override
  protected Action[] createActions() {
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
    String topic = getHelpTopic();
    if (topic != null) {
      actions.add(getHelpAction());
    }
    return actions.toArray(new Action[0]);
  }

  protected String getHelpTopic() {
    return myEditor.getHelpTopic();
  }

  @Override
  protected void doHelpAction() {
    String topic = getHelpTopic();
    if (topic != null) {
      HelpManager.getInstance().invokeHelp(topic);
    }
  }

  @Override
  public void doOKAction() {
    if (myEditor.apply()) {
      StoreUtil.saveProjectsAndApp(true);
      super.doOKAction();
    }
  }

  @Override
  public void doCancelAction(AWTEvent source) {
    if (source instanceof KeyEvent || source instanceof ActionEvent) {
      if (!myEditor.cancel()) {
        return;
      }
    }
    super.doCancelAction(source);
  }

  static ShortcutSet getFindActionShortcutSet() {
    AnAction action = ActionManager.getInstance().getAction(ACTION_FIND);
    return action == null ? null : action.getShortcutSet();
  }
}
