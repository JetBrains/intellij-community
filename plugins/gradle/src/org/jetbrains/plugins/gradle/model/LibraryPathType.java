package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NotNull;

/**
 * Note that current enum duplicates {@link OrderRootType}. We can't use the later directly because it's not properly setup
 * for serialization/deserialization.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:37 PM
 */
public enum LibraryPathType {
  
  BINARY(OrderRootType.CLASSES), SOURCE(OrderRootType.SOURCES), DOC(OrderRootType.DOCUMENTATION);

  private final transient OrderRootType myRootType;
  
  LibraryPathType(@NotNull OrderRootType rootType) {
    myRootType = rootType;
  }

  @NotNull
  public OrderRootType getRootType() {
    return myRootType;
  }
}
