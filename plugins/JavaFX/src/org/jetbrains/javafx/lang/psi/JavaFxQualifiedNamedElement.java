package org.jetbrains.javafx.lang.psi;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxQualifiedNamedElement extends JavaFxElement {
  @Nullable
  JavaFxQualifiedName getQualifiedName();
}
