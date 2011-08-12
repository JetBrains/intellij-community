package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import java.awt.*;

/**
 * Reliefs implementation of the {@link GradleProjectStructureNodeSettings#getComponent() GUI for managing gradle entity settings}.
 * <p/>
 * Basically, assumes that all settings are displayed at the two-columns view where left column holds setting name and the right one
 * holds GUI control that shows current setting value and/or allows to modify it.
 * <p/>
 * See method-level documentation for more details.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 3:03 PM
 */
public class GradleProjectSettingsBuilder {

  private final JPanel             myResult            = new JPanel(new GridBagLayout());
  private final GridBagConstraints myLabelConstraint   = new GridBagConstraints();
  private final GridBagConstraints myControlConstraint = new GridBagConstraints();

  public GradleProjectSettingsBuilder() {
    myLabelConstraint.anchor = myControlConstraint.anchor = GridBagConstraints.WEST;
    
    myControlConstraint.gridwidth = GridBagConstraints.REMAINDER;
    myControlConstraint.weightx = 1;
    myControlConstraint.fill = GridBagConstraints.HORIZONTAL;
  }

  /**
   * Instructs current builder to use given property key for the setting label name retrieval and given control for
   * the settings value processing
   * 
   * @param labelKey  bundle key to use for retrieving setting's label name
   * @param control   GUI control for managing target setting's value
   */
  public void add(@NotNull @PropertyKey(resourceBundle = GradleBundle.PATH_TO_BUNDLE) String labelKey, @NotNull JComponent control) {
    JLabel label = new JLabel(GradleBundle.message(labelKey));
    myResult.add(label, myLabelConstraint);
    myResult.add(control, myControlConstraint);

    if (myLabelConstraint.insets.top <= 0) {
      myLabelConstraint.insets.top = myControlConstraint.insets.top = 15;
    } 
  }

  /**
   * @return    GUI component that shows all of the stuff registered earlier via {@link #add(String, JComponent)}
   */
  public JComponent build() {
    // We don't check that this method hasn't been called already. Add that check if necessary.
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.weighty = 1;
    constraints.fill = GridBagConstraints.VERTICAL;
    myResult.add(new JLabel(""), constraints);
    return myResult;
  }
}
