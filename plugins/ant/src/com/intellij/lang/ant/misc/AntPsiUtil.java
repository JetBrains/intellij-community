package com.intellij.lang.ant.misc;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntImport;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

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

  /**
   * Returns imported ant files for a project.
   */
  @NotNull
  public static AntFile[] getImportedFiles(final AntProject project) {
    return getImportedFiles(project, null);
  }

  /**
   * Returns imported ant files for a project from the first project element upto the anchor.
   */
  @NotNull
  public static AntFile[] getImportedFiles(final AntProject project, final AntElement anchor) {
    final HashSet<PsiElement> set = PsiElementHashSetSpinAllocator.alloc();
    try {
      for (PsiElement child : project.getChildren()) {
        if (child == anchor) break;
        if (child instanceof AntImport) {
          set.add(((AntImport)child).getAntFile());
        }
      }
      return (set.size() > 0) ? set.toArray(new AntFile[set.size()]) : NO_FILES;
    }
    finally {
      PsiElementHashSetSpinAllocator.dispose(set);
    }
  }

  private static final AntFile[] NO_FILES = new AntFile[0];
}
