package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxObjectLiteralInit extends JavaFxElement, PsiNamedElement {
  JavaFxObjectLiteralInit[] EMPTY_ARRAY = new JavaFxObjectLiteralInit[0];

  @NotNull
  JavaFxReferenceElement getReferenceElement();

  @Nullable
  JavaFxExpression getInitializer();
}
