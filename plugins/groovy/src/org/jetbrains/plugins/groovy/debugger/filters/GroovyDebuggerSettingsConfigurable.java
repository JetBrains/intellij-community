package org.jetbrains.plugins.groovy.debugger.filters;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author ilyas
 */
public class GroovyDebuggerSettingsConfigurable implements Configurable {
  private JCheckBox myIgnoreGroovyMethods;
  private JPanel myPanel;
  private boolean isModified = false;
  private final GroovyDebuggerSettings mySettings;

  public GroovyDebuggerSettingsConfigurable(final GroovyDebuggerSettings settings) {
    mySettings = settings;
    final Boolean flag = settings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS;
    myIgnoreGroovyMethods.setSelected(flag == null || flag.booleanValue());

    myIgnoreGroovyMethods.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        isModified = mySettings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS.booleanValue() != myIgnoreGroovyMethods.isSelected();
      }
    });
  }

  @Nls
  public String getDisplayName() {
    return GroovyBundle.message("groovy.debug.caption");
  }

  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }

  public String getHelpTopic() {
    return "reference.idesettings.debugger.groovy";
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return isModified;
  }

  public void apply() throws ConfigurationException {
    if (isModified) {
      mySettings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = myIgnoreGroovyMethods.isSelected();
    }
    isModified = false;
  }

  public void reset() {
    final Boolean flag = mySettings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS;
    myIgnoreGroovyMethods.setSelected(flag == null || flag.booleanValue());
  }

  public void disposeUIResources() {
  }
}
