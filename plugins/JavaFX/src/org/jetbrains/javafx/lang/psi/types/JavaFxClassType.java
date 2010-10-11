package org.jetbrains.javafx.lang.psi.types;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxClassType {
  @Nullable
  PsiElement getClassElement(final Project project);
}
