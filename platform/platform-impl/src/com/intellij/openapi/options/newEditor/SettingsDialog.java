// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.CommonBundle;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.plugins.newui.EventHandler;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.SearchTextField.FindAction;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_FIND;

public class SettingsDialog extends DialogWrapper implements UiCompatibleDataProvider {
  public static final String DIMENSION_KEY = "SettingsEditor";

  private final String dimensionServiceKey;
  private final AbstractEditor editor;
  private final boolean isApplyButtonNeeded;
  private final boolean isResetButtonNeeded;
  private final JLabel myHintLabel = new JLabel();

  public SettingsDialog(Project project, String key, @NotNull Configurable configurable, boolean showApplyButton, boolean showResetButton) {
    super(project, true);
    dimensionServiceKey = key;
    editor = new SingleSettingEditor(myDisposable, configurable);
    isApplyButtonNeeded = showApplyButton;
    isResetButtonNeeded = showResetButton;
    init(configurable, project);
  }

  public SettingsDialog(@NotNull Component parent, String key, @NotNull Configurable configurable, boolean showApplyButton, boolean showResetButton) {
    super(parent, true);
    dimensionServiceKey = key;
    editor = new SingleSettingEditor(myDisposable, configurable);
    isApplyButtonNeeded = showApplyButton;
    isResetButtonNeeded = showResetButton;
    init(configurable, null);
  }

  public SettingsDialog(@NotNull Project project, @NotNull List<? extends ConfigurableGroup> groups, @Nullable Configurable configurable, @Nullable String filter) {
    super(project, true);
    dimensionServiceKey = DIMENSION_KEY;
    editor = new SettingsEditor(myDisposable, project, groups, configurable, filter, this::treeViewFactory, this::spotlightPainterFactory);
    isApplyButtonNeeded = true;
    isResetButtonNeeded = false;
    init(null, project);
  }

  public SettingsDialog(@NotNull Project project,
                        @Nullable Component parentComponent,
                        @NotNull List<? extends ConfigurableGroup> groups,
                        @Nullable Configurable configurable,
                        @Nullable String filter) {
    super(project, parentComponent, true, IdeModalityType.IDE);
    dimensionServiceKey = DIMENSION_KEY;
    editor = new SettingsEditor(myDisposable, project, groups, configurable, filter, this::treeViewFactory, this::spotlightPainterFactory);
    isApplyButtonNeeded = true;
    isResetButtonNeeded = false;
    init(null, project);
  }

  protected final AbstractEditor getEditor() {
    return editor;
  }

  protected @NotNull SettingsTreeView treeViewFactory(@NotNull SettingsFilter filter, @NotNull List<? extends ConfigurableGroup> groups) {
    return new SettingsTreeView(filter, groups);
  }

  @ApiStatus.Internal
  protected @NotNull SpotlightPainter spotlightPainterFactory(
    @Nullable Project project,
    @NotNull JComponent target,
    @NotNull Disposable parent,
    @NotNull Function1<? super SpotlightPainter, Unit> updater
  ) {
    return new SpotlightPainter(target, updater);
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
    if (UISettings.isIdeHelpTooltipEnabled()) {
      new HelpTooltip().setDescription(ActionsBundle.actionDescription("HelpTopics")).installOn(helpButton);
    }
    else {
      super.setHelpTooltip(helpButton);
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    DataSink.uiDataSnapshot(sink, editor);
  }

  @Override
  protected String getDimensionServiceKey() {
    return dimensionServiceKey;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return editor.getPreferredFocusedComponent();
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected JComponent createCenterPanel() {
    return editor;
  }

  @Override
  protected @Nullable JPanel createSouthAdditionalPanel() {
    JPanel panel = new NonOpaquePanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.emptyLeft(10));
    panel.add(myHintLabel);
    myHintLabel.setEnabled(false);
    return panel;
  }

  protected void tryAddOptionsListener(OptionsEditorColleague colleague) {
    if (editor instanceof SettingsEditor) {
      ((SettingsEditor)editor).addOptionsListener(colleague);
    }
  }

  @Override
  protected Action @NotNull [] createActions() {
    ArrayList<Action> actions = new ArrayList<>();
    actions.add(getOKAction());
    actions.add(getCancelAction());
    Action apply = editor.getApplyAction();
    if (apply != null && isApplyButtonNeeded) {
      actions.add(new ApplyActionWrapper(apply));
    }
    Action reset = editor.getResetAction();
    if (reset != null && isResetButtonNeeded) {
      actions.add(reset);
    }
    if (getHelpId() != null) {
      actions.add(getHelpAction());
    }
    return actions.toArray(new Action[0]);
  }

  @Override
  protected @Nullable String getHelpId() {
    return editor.getHelpTopic();
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
    if (editor.apply()) {
      if (scheduleSave) {
        SaveAndSyncHandler.getInstance().scheduleSave(new SaveAndSyncHandler.SaveTask(null, /* forceSavingAllSettings = */ true));
      }
      super.doOKAction();
    }
  }

  @Override
  public void doCancelAction(AWTEvent source) {
    if (source instanceof KeyEvent || source instanceof ActionEvent) {
      if (!editor.cancel(source)) {
        return;
      }
    }
    super.doCancelAction(source);
  }

  static @Nullable ShortcutSet getFindActionShortcutSet() {
    return EventHandler.getShortcuts(ACTION_FIND);
  }

  private final class ApplyActionWrapper extends AbstractAction {
    private final @NotNull Action delegate;

    ApplyActionWrapper(@NotNull Action delegate) {
      this.delegate = delegate;
      superSetEnabled(delegate.isEnabled());
      delegate.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if ("enabled".equals(evt.getPropertyName())) {
            superSetEnabled((Boolean)evt.getNewValue());
          }
        }
      });

      if (delegate instanceof AbstractAction abstractAction) {
        Object[] keys = abstractAction.getKeys();
        if (keys != null) {
          for (Object key : keys) {
            if (key instanceof String stringKey) {
              putValue(stringKey, abstractAction.getValue(stringKey));
            }
          }
        }
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      delegate.actionPerformed(e);
      ApplicationManager.getApplication().getMessageBus().syncPublisher(SettingsDialogListener.TOPIC).afterApply(editor);
    }

    @Override
    public void setEnabled(boolean newValue) {
      delegate.setEnabled(newValue);
    }

    private void superSetEnabled(boolean newValue) {
      super.setEnabled(newValue);
    }
  }
}
