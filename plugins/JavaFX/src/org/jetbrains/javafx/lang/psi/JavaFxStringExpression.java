package org.jetbrains.javafx.lang.psi;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxStringExpression extends JavaFxLiteralExpression {
  JavaFxStringCompoundElement[] getStringElements();
}
