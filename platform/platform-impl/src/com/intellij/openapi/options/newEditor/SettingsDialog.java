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
package com.intellij.openapi.options.newEditor;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.ui.border.CustomLineBorder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * @author Sergey.Malenkov
 */
public final class SettingsDialog extends DialogWrapper implements DataProvider {
  private final String myDimensionServiceKey;
  private final AbstractEditor myEditor;

  public SettingsDialog(Project project, @NotNull String key, @NotNull Configurable configurable, boolean showApplyButton) {
    super(project, true);
    myDimensionServiceKey = key;
    myEditor = new ConfigurableEditor(myDisposable, configurable, showApplyButton);
    init(configurable);
  }

  public SettingsDialog(@NotNull Component parent, @NotNull String key, @NotNull Configurable configurable, boolean showApplyButton) {
    super(parent, true);
    myDimensionServiceKey = key;
    myEditor = new ConfigurableEditor(myDisposable, configurable, showApplyButton);
    init(configurable);
  }

  public SettingsDialog(@NotNull Project project, @NotNull ConfigurableGroup[] groups, Configurable configurable, String filter) {
    super(project, true);
    myDimensionServiceKey = "SettingsEditor";
    myEditor = new SettingsEditor(myDisposable, project, groups, configurable, filter);
    init(null);
  }

  private void init(Configurable configurable) {
    String name = configurable == null ? null : configurable.getDisplayName();
    setTitle(name == null ? CommonBundle.settingsTitle() : name.replaceAll("\n", " "));
    init();
  }

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

  @Nullable
  @Override
  protected Border createContentPaneBorder() {
    return BorderFactory.createEmptyBorder();
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    JComponent panel = super.createSouthPanel();
    if (panel != null) {
      panel.setBorder(BorderFactory.createCompoundBorder(
        new CustomLineBorder(OnePixelDivider.BACKGROUND, 1, 0, 0, 0),
        BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    return myEditor;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    ArrayList<Action> actions = new ArrayList<Action>();
    actions.add(getOKAction());
    actions.add(getCancelAction());
    Action apply = myEditor.getApplyAction();
    if (apply != null) {
      actions.add(apply);
    }
    Action reset = myEditor.getResetAction();
    if (reset != null) {
      actions.add(reset);
    }
    actions.add(getHelpAction());
    return actions.toArray(new Action[actions.size()]);
  }

  @Override
  protected void doHelpAction() {
    String topic = myEditor.getHelpTopic();
    if (topic != null) {
      HelpManager.getInstance().invokeHelp(topic);
    }
  }

  @Override
  protected void doOKAction() {
    if (myEditor.apply()) {
      ApplicationManager.getApplication().saveAll();
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
}
