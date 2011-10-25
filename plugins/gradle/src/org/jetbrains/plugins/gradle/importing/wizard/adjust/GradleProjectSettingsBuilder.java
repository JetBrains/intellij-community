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
  
  public enum InsetSize {
    NONE(0), SMALL(3), NORMAL(15);
    
    private final int myInsetValue;

    InsetSize(int insetValue) {
      myInsetValue = insetValue;
    }

    public int getValue() {
      return myInsetValue;
    }
  }
  
  private final JPanel             myResult            = new JPanel(new GridBagLayout());
  private final GridBagConstraints myConstraint   = new GridBagConstraints();

  public GradleProjectSettingsBuilder() {
    myConstraint.anchor = GridBagConstraints.WEST;
    myConstraint.gridwidth = GridBagConstraints.REMAINDER;
    myConstraint.weightx = 1;
    myConstraint.fill = GridBagConstraints.HORIZONTAL;
  }

  public void add(@NotNull JComponent component) {
    add(component, InsetSize.NORMAL);
  }
  
  /**
   * Instructs current builder to use given component for the target property management.
   * 
   * @param component   component to use
   * @param insetSize   top insets to use
   */
  public void add(@NotNull JComponent component, @NotNull InsetSize insetSize) {
    myConstraint.insets.top = insetSize.getValue();
    myResult.add(component, myConstraint);
  }
  
  /**
   * Instructs current builder to use given property key for the setting label name retrieval and given control for
   * the settings value processing
   * 
   * @param labelKey  bundle key to use for retrieving setting's label name
   * @param control   GUI control for managing target setting's value
   */
  public void add(@NotNull @PropertyKey(resourceBundle = GradleBundle.PATH_TO_BUNDLE) String labelKey, @NotNull JComponent control) {
    myConstraint.insets.top = InsetSize.NORMAL.getValue();
    JLabel label = new JLabel(GradleBundle.message(labelKey));
    myResult.add(label, myConstraint);
    myConstraint.insets.top = InsetSize.SMALL.getValue();
    myResult.add(control, myConstraint);
  }

  /**
   * Instructs current builder to expose target property using the given UI controls.
   * 
   * @param keyComponent    control that provides information about the target property (e.g. its name or description)
   * @param valueComponent  control that holds available property values and (possibly) allows to choose between them
   */
  public void add(@NotNull JComponent keyComponent, @NotNull JComponent valueComponent) {
    myConstraint.insets.top = InsetSize.NORMAL.getValue();
    myResult.add(keyComponent, myConstraint);
    myConstraint.insets.top = InsetSize.SMALL.getValue();
    myResult.add(valueComponent, myConstraint);
  }
  
  /**
   * @return    GUI component that shows all of the stuff registered earlier via {@link #add(String, JComponent)}
   */
  @NotNull
  public JComponent build() {
    // We don't check that this method hasn't been called already. Add that check if necessary.
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.weighty = 1;
    constraints.fill = GridBagConstraints.VERTICAL;
    myResult.add(new JLabel(""), constraints);
    return myResult;
  }
}
