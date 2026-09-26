package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.test.source.ClassSource;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

@ApiStatus.Internal
public class InternalDefaultClassSource implements Serializable, ClassSource {

  private final String className;

  public InternalDefaultClassSource(String className) {
    this.className = className;
  }

  @Override
  public String getClassName() {
    return className;
  }
}
