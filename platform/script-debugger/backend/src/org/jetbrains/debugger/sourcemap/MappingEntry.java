package org.jetbrains.debugger.sourcemap;

import org.jetbrains.annotations.Nullable;

/**
 * Mapping entry in the source map
 */
public abstract class MappingEntry {
  public abstract int getGeneratedColumn();

  public abstract int getGeneratedLine();

  public abstract int getSourceLine();

  public abstract int getSourceColumn();

  public int getSource() {
    return -1;
  }

  @Nullable
  public String getName() {
    return null;
  }
}
