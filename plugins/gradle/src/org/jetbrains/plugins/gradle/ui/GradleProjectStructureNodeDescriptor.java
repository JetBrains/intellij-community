package org.jetbrains.plugins.gradle.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.id.GradleEntityId;

import javax.swing.*;

/**
 * Descriptor for the node of 'project structure view' derived from gradle.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:47 PM
 * @param <T>   target element type
 */
public class GradleProjectStructureNodeDescriptor<T> extends PresentableNodeDescriptor<T> {

  private TextAttributesKey myAttributes = GradleTextAttributes.GRADLE_NO_CHANGE;

  private final T myData;

  @SuppressWarnings("NullableProblems")
  public GradleProjectStructureNodeDescriptor(@NotNull T data, @NotNull String text, @Nullable Icon icon) {
    super(null, null);
    myData = data;
    myOpenIcon = myClosedIcon = icon;
    myName = text;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setAttributesKey(myAttributes);
    presentation.setPresentableText(myName);
    presentation.setOpenIcon(myOpenIcon);
    presentation.setClosedIcon(myClosedIcon);
  }

  @NotNull
  @Override
  public T getElement() {
    return myData;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public TextAttributesKey getAttributes() {
    return myAttributes;
  }

  public void setAttributes(@NotNull TextAttributesKey attributes) {
    myAttributes = attributes;
    // TODO den remove the type check
    if (myData instanceof GradleEntityId) {
      final GradleEntityId id = (GradleEntityId)myData;
      GradleEntityOwner owner = id.getOwner();
      if (attributes == GradleTextAttributes.GRADLE_LOCAL_CHANGE) {
        owner = GradleEntityOwner.GRADLE;
      }
      else if (attributes == GradleTextAttributes.GRADLE_NO_CHANGE || attributes == GradleTextAttributes.INTELLIJ_LOCAL_CHANGE) {
        owner = GradleEntityOwner.INTELLIJ;
      }
      id.setOwner(owner);
    }
    update();
  }
}