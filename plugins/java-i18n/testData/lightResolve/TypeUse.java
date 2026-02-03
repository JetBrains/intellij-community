package org.jetbrains.annotations;

import java.lang.annotation.*;
import java.util.function.*;

@Target({ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.TYPE_USE})
public @interface PropertyKey {
  @NonNls String resourceBundle();
}

class PropertyRef {
  Supplier<@PropertyKey(resourceBundle = "Bundle1") String> getKeySupplier() {
    return () -> "sam<caret>e.name";
  }
}