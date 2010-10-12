package org.jetbrains.javafx.lang.psi;


import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:21:17
 */
public interface JavaFxExpression extends JavaFxElement {
  JavaFxExpression[] EMPTY_ARRAY = new JavaFxExpression[0];

  @Nullable
  PsiType getType();
}
