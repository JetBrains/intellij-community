package org.jetbrains.javafx.lang.psi.types;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxFunctionType {
  @Nullable
  PsiType getReturnType();
}
