package org.jetbrains.javafx.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxReferenceElement extends JavaFxElement {
  JavaFxReferenceElement[] EMPTY_ARRAY = new JavaFxReferenceElement[0];

  @Nullable
  ASTNode getNameNode();

  JavaFxQualifiedName getQualifiedName();

  @Nullable
  JavaFxExpression getQualifier();
}
