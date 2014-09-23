package org.editorconfig.settings;

import com.intellij.application.options.GeneralCodeStyleOptionsProvider;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dennis.Ushakov
 */
public class EditorConfigConfigurable extends CodeStyleSettingsProvider implements GeneralCodeStyleOptionsProvider {
  private JBCheckBox myEnabled;

  @Nullable
  @Override
  public JComponent createComponent() {
    myEnabled = new JBCheckBox("Enable EditorConfig support");
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder("EditorConfig", false));
    panel.add(myEnabled);
    final JLabel warning = new JLabel("EditorConfig may override the IDE code style settings");
    warning.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    warning.setBorder(IdeBorderFactory.createEmptyBorder(0, 20, 0, 0));
    panel.add(warning);
    return panel;
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return myEnabled.isSelected() != settings.getCustomSettings(EditorConfigSettings.class).ENABLED;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    settings.getCustomSettings(EditorConfigSettings.class).ENABLED = myEnabled.isSelected();
  }

  @Override
  public void reset(CodeStyleSettings settings) {
    myEnabled.setSelected(settings.getCustomSettings(EditorConfigSettings.class).ENABLED);
  }

  @Override
  public void disposeUIResources() {
    myEnabled = null;
  }

  @NotNull
  @Override
  public Configurable createSettingsPage(CodeStyleSettings settings, CodeStyleSettings originalSettings) {
    return null;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {}

  @Override
  public void reset() {}

  @Override
  public boolean hasSettingsPage() {
    return false;
  }

  @Nullable
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new EditorConfigSettings(settings);
  }
}
