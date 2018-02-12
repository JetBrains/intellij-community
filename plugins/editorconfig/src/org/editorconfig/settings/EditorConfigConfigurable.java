package org.editorconfig.settings;

import com.intellij.application.options.GeneralCodeStyleOptionsProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dennis.Ushakov
 */
public class EditorConfigConfigurable extends CodeStyleSettingsProvider implements GeneralCodeStyleOptionsProvider {
  private JBCheckBox myEnabled;

  @Nullable
  @Override
  public JComponent createComponent() {
    myEnabled = new JBCheckBox("Enable EditorConfig support");
    final JPanel result = new JPanel();
    result.setLayout(new BoxLayout(result, BoxLayout.LINE_AXIS));
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    result.setBorder(IdeBorderFactory.createTitledBorder("EditorConfig", false));
    panel.add(myEnabled);
    final JLabel warning = new JLabel("EditorConfig may override the IDE code style settings");
    warning.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    warning.setBorder(JBUI.Borders.emptyLeft(20));
    panel.add(warning);
    panel.setAlignmentY(Component.TOP_ALIGNMENT);
    result.add(panel);
    final JButton export = new JButton("Export");
    export.addActionListener((event) -> {
      final Component parent = UIUtil.findUltimateParent(result);
      if (parent instanceof IdeFrame) {
        Utils.export(((IdeFrame)parent).getProject());
      }
    });
    export.setAlignmentY(Component.TOP_ALIGNMENT);
    result.add(export);
    return result;
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return myEnabled.isSelected() != settings.getCustomSettings(EditorConfigSettings.class).ENABLED;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    boolean newValue = myEnabled.isSelected();
    settings.getCustomSettings(EditorConfigSettings.class).ENABLED = newValue;
    MessageBus bus = ApplicationManager.getApplication().getMessageBus();
    bus.syncPublisher(EditorConfigSettings.EDITOR_CONFIG_ENABLED_TOPIC).valueChanged(newValue);
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
  public boolean hasSettingsPage() {
    return false;
  }

  @Nullable
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new EditorConfigSettings(settings);
  }
}
