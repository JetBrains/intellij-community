package com.intellij.driver.model.transport;

import java.io.Serial;
import java.io.Serializable;

public record Ref(
  String id,
  String className,
  int identityHashCode,
  String asString
) implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  public static final String BACKEND_REFERENCE_PREFIX = "remdevHost_";
  public static final String FRONTEND_REFERENCE_PREFIX = "jbClient_";

  public boolean isBackendReference() {
    return id.startsWith(BACKEND_REFERENCE_PREFIX);
  }
}