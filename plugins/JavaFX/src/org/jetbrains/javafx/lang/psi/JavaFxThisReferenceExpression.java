package org.jetbrains.javafx.lang.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxThisReferenceExpression extends JavaFxLiteralExpression, JavaFxReferenceExpression {
  @Nullable
  JavaFxClassDefinition getContainingClass();
}
