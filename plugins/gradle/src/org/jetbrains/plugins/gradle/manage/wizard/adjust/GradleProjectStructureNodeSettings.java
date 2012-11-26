package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Defines contract for the control that exposes settings of particular node of the 'project structure' view derived from gradle.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:50 PM
 */
public interface GradleProjectStructureNodeSettings {

  /**
   * Asks current component to validate current model state, i.e. expected actions sequence is below:
   * <p/>
   * <pre>
   * <ol>
   *   <li>
   *     Particular node settings are {@link #getComponent() exposed} to the end user;
   *   </li>
   *   <li>He or she tweaks the settings;</li>
   *   <li>This method is called on request to finish editing;</li>
   * </ol>
   * </pre>
   * 
   * @return    <code>true</code> if everything is ok; <code>false</code> otherwise
   */
  boolean validate();

  /**
   * Asks current control to refresh, i.e. show values stored at the underlying model.  
   */
  void refresh();
  
  @NotNull
  JComponent getComponent();
}
