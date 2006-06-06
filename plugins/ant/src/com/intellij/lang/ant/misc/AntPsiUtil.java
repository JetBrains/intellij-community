package com.intellij.lang.ant.misc;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import org.jetbrains.annotations.Nullable;

public class AntPsiUtil {

  private AntPsiUtil() {
  }

  /**
   * Returns an element under Ant project which is an ancestor of the specified element.
   * Returns null for AntProject and AntFile.
   */
  @Nullable
  public static AntElement getSubProjectElement(AntElement element) {
    AntElement parent = element.getAntParent();
    while (true) {
      if (parent == null) {
        element = null;
        break;
      }
      if (parent instanceof AntProject) break;
      element = parent;
      parent = parent.getAntParent();
    }
    return element;
  }
}
