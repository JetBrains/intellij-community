package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.test.source.ClasspathResourceSource;
import org.gradle.tooling.events.test.source.FilePosition;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

@ApiStatus.Internal
public class InternalDefaultClasspathResourceSource implements Serializable, ClasspathResourceSource {

  private final String classpathResourceName;
  private final FilePosition position;

  public InternalDefaultClasspathResourceSource(String classpathResourceName, FilePosition position) {
    this.classpathResourceName = classpathResourceName;
    this.position = position;
  }

  @Override
  public String getClasspathResourceName() {
    return classpathResourceName;
  }

  @Override
  public FilePosition getPosition() {
    return position;
  }
}
