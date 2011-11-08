package org.jetbrains.plugins.gradle.sync;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:58 PM
 */
public class GradleProjectStructureChangesPanel extends JPanel {
  
  private final GradleProjectStructureChangesModel myModel;

  public GradleProjectStructureChangesPanel(@NotNull GradleProjectStructureChangesModel model) {
    myModel = model;
  }
}
