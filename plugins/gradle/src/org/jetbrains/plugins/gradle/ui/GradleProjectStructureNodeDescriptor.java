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
public class GradleProjectStructureNodeDescriptor<T extends GradleEntityId> extends PresentableNodeDescriptor<T> {

  private TextAttributesKey myAttributes = GradleTextAttributes.NO_CHANGE;

  private final T myId;

  @SuppressWarnings("NullableProblems")
  public GradleProjectStructureNodeDescriptor(@NotNull T id, @NotNull String text, @Nullable Icon icon) {
    super(null, null);
    myId = id;
    setIcon(icon);
    myName = text;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setAttributesKey(myAttributes);
    presentation.setPresentableText(myName);
    presentation.setIcon(getIcon());
  }

  @NotNull
  @Override
  public T getElement() {
    return myId;
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
    GradleEntityOwner owner = myId.getOwner();
    if (attributes == GradleTextAttributes.GRADLE_LOCAL_CHANGE) {
      owner = GradleEntityOwner.GRADLE;
    }
    else if (attributes == GradleTextAttributes.NO_CHANGE || attributes == GradleTextAttributes.INTELLIJ_LOCAL_CHANGE) {
      owner = GradleEntityOwner.INTELLIJ;
    }
    myId.setOwner(owner);
    update();
  }

  public void setToolTip(@NotNull String text) {
    getTemplatePresentation().setTooltip(text);
    update();
  }
}
