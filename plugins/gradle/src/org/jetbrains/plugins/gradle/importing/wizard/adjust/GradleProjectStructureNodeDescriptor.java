package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.ide.util.treeView.NodeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleEntity;
import org.jetbrains.plugins.gradle.importing.model.Named;

import javax.swing.*;

/**
 * Descriptor for the node of 'project structure view' derived from gradle.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:47 PM
 */
public class GradleProjectStructureNodeDescriptor extends NodeDescriptor<GradleEntity> {

  private final GradleEntity myData;

  @SuppressWarnings("NullableProblems")
  GradleProjectStructureNodeDescriptor(@NotNull GradleEntity data, @NotNull String text, @NotNull Icon icon) {
    super(null, null);
    myData = data;
    myOpenIcon = myClosedIcon = icon;
    myName = text;
  }

  @Override
  public boolean update() {
    return true;
  }

  @Override
  public GradleEntity getElement() {
    return myData;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }
}