package org.jetbrains.android.dom.drawable;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AnimationListDomFileDescription extends AndroidResourceDomFileDescription<AnimationList> {
  @NonNls static final String ANIMATION_LIST_TAG = "animation-list";

  public AnimationListDomFileDescription() {
    super(AnimationList.class, ANIMATION_LIST_TAG, "drawable");
  }

  @Override
  public boolean acceptsOtherRootTagNames() {
    return true;
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    if (!super.isMyFile(file, module)) {
      return false;
    }

    final XmlTag rootTag = file.getRootTag();
    if (rootTag == null) {
      return false;
    }

    return ANIMATION_LIST_TAG.equals(rootTag.getName());
  }
}

