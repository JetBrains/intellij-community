package org.jetbrains.plugins.groovy.intentions.utils;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;

public class TreeUtil {
  private TreeUtil() {
    super();
  }

  @Nullable
  public static PsiElement getNextLeaf(PsiElement element) {
    if (element == null) {
      return null;
    }
    final PsiElement sibling = element.getNextSibling();
    if (sibling == null) {
      final PsiElement parent = element.getParent();
      return getNextLeaf(parent);
    }
    return getFirstLeaf(sibling);
  }

  private static PsiElement getFirstLeaf(PsiElement element) {
    final PsiElement[] children = element.getChildren();
    if (children.length == 0) {
      return element;
    }
    return getFirstLeaf(children[0]);
  }

  @Nullable
  public static PsiElement getPrevLeaf(PsiElement element) {
    if (element == null) {
      return null;
    }
    final PsiElement sibling = element.getPrevSibling();
    if (sibling == null) {
      final PsiElement parent = element.getParent();
      return getPrevLeaf(parent);
    }
    return getLastLeaf(sibling);
  }

  private static PsiElement getLastLeaf(PsiElement element) {
    final PsiElement[] children = element.getChildren();
    if (children.length == 0) {
      return element;
    }
    return getLastLeaf(children[children.length - 1]);
  }
}
