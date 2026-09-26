package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.test.source.FilePosition;
import org.gradle.tooling.events.test.source.FileSource;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.Serializable;

@ApiStatus.Internal
public class InternalDefaultFileSource implements Serializable, FileSource {

  private final File file;
  private final FilePosition position;

  public InternalDefaultFileSource(File file, FilePosition position) {
    this.file = file;
    this.position = position;
  }

  @Override
  public final File getFile() {
    return this.file;
  }

  @Override
  public FilePosition getPosition() {
    return position;
  }
}
