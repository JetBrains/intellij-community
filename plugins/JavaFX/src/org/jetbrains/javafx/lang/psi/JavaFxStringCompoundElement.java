package org.jetbrains.javafx.lang.psi;

import com.intellij.lang.ASTNode;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxStringCompoundElement extends JavaFxElement {
  JavaFxStringCompoundElement[] EMPTY_ARRAY = new JavaFxStringCompoundElement[0];

  ASTNode[] getStringNodes();
}
