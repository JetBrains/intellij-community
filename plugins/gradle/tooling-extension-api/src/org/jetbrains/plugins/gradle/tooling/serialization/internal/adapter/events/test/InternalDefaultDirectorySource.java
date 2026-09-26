package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.test.source.DirectorySource;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.Serializable;

@ApiStatus.Internal
public class InternalDefaultDirectorySource implements Serializable, DirectorySource {

  private final File file;

  public InternalDefaultDirectorySource(File file) {
    this.file = file;
  }

  @Override
  public final File getFile() {
    return this.file;
  }
}
