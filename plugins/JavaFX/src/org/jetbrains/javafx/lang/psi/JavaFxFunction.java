package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public interface JavaFxFunction extends JavaFxElement {
  @Nullable
  JavaFxSignature getSignature();

  PsiType getReturnType();

  @Nullable
  JavaFxBlockExpression getCodeBlock();
}
