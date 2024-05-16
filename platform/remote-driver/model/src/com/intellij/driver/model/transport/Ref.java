package com.intellij.driver.model.transport;

import com.intellij.driver.model.RdTarget;

import java.io.Serial;
import java.io.Serializable;

public record Ref(
  String id,
  String className,
  int identityHashCode,
  String asString,
  RdTarget rdTarget
) implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
}