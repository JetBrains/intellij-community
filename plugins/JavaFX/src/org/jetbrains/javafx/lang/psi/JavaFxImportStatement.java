package org.jetbrains.javafx.lang.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:41:44
 */
public interface JavaFxImportStatement extends JavaFxQualifiedNamedElement {
  JavaFxImportStatement[] EMPTY_ARRAY = new JavaFxImportStatement[0];

  @Nullable
  JavaFxReferenceElement getImportReference();

  boolean isOnDemand();
}
