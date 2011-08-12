package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Defines contract for the entity that exposes settings of particular node of the 'project structure' view derived from gradle.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:50 PM
 */
public interface GradleProjectStructureNodeSettings {

  /**
   * Asks current component to store all changes made by user to the underlying model.
   * 
   * @return    <code>true</code> if everything is ok; <code>false</code> otherwise
   */
  boolean commit();
  
  @NotNull
  JComponent getComponent();
}
