package org.jetbrains.plugins.gradle.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Descriptor for the node of 'project structure view' derived from gradle.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:47 PM
 * @param <T>   target element type
 */
public class GradleProjectStructureNodeDescriptor<T> extends PresentableNodeDescriptor<T> {

  private final T myData;

  @SuppressWarnings("NullableProblems")
  public GradleProjectStructureNodeDescriptor(@NotNull T data, @NotNull String text, @NotNull Icon icon) {
    super(null, null);
    myData = data;
    myOpenIcon = myClosedIcon = icon;
    myName = text;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setForcedTextForeground(Color.RED); 
  }

  @Override
  public T getElement() {
    return myData;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }
}