package org.jetbrains.android.dom.animator;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AnimatorDomFileDescription extends AndroidResourceDomFileDescription<AnimatorElement> {
  public AnimatorDomFileDescription() {
    super(AnimatorElement.class, "set", ResourceFolderType.ANIMATOR.getName());
  }

  @Override
  public boolean acceptsOtherRootTagNames() {
    return true;
  }

  public static boolean isAnimatorFile(@NotNull final XmlFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return new AnimatorDomFileDescription().isMyFile(file, null);
      }
    });
  }
}
