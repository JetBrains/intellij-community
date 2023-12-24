// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.durablemaps;

import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Trait for the (durable) data structures that may need compaction (defragmentation)
 */
@ApiStatus.Internal
public interface Compactable<C extends Compactable<C>> {

  @NotNull CompactionScore compactionScore() throws IOException;

  /**
   * Method should return a _copy_ of current data structure -- i.e. original structure is unchanged, and still
   * operational, but the returned instance contains same data, compacted.
   * Usually original data structure should not be modified during the compaction --
   */
  public @NotNull <C1 extends C> C1 compact(@NotNull ThrowableComputable<C1, ? extends IOException> compactedMapFactory) throws IOException;

  class CompactionScore {
    private final double score;

    public CompactionScore(double score) {
      this.score = score;
    }

    /**
     * @return how much compaction is worthwhile/needed.
     * <pre>
     * Value is in [0..1], expected approximate meaning is following:
     * < 0.1  =>   almost nothing to compact
     * > 0.5  =>   compaction maybe worthwhile, but not urgently needed
     * > 0.9  =>   compaction needed urgently
     * = 1    =>   can't work without compaction
     * </pre>
     */
    public double score() {
      return score;
    }

    public boolean compactionNotNeeded(){
      return score < 0.1;
    }

    public boolean compactionNeeded(){
      return score > 0.5;
    }

    public boolean compactionNeededUrgently(){
      return score > 0.9;
    }

    @Override
    public String toString() {
      return "CompactionScore[=" + score + ']';
    }
  }

  /**
   * In some cases compaction is not an option, but a requirement to continue operation.
   * Methods of data structure should throw this exception to indicate that no operations could be done until compaction.
   */
  class CompactionIsRequiredException extends IOException {
    public CompactionIsRequiredException(String message) {
      super(message);
    }

    public CompactionIsRequiredException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
