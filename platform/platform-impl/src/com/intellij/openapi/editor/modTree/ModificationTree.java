// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.modTree;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Persistent mapping between offsets in version 0 and offsets in the current version.
 * <p>
 * This structure stores only coordinate mapping.
 * It does not store text, intervals, or edit history.
 * <p>
 * Semantics:
 * - deleted original text is represented as missing version-0 ranges
 * - inserted current text is represented as gaps between mapped original ranges
 * - projection is deterministic and right-biased:
 * - version0 -> current: boundaries map after inserted text
 * - current -> version0: inserted gaps map to the version-0 boundary after the gap
 */
@ApiStatus.Internal
public interface ModificationTree {

  /**
   * Maps an offset from version 0 to the current version.
   * <p>
   * If the version-0 offset belongs to surviving original text:
   * <p>
   * result = offsetInVersion0 + effectiveDelta
   * <p>
   * If the offset is inside deleted original text, it maps to the collapsed
   * current position after the deleted/replaced region.
   *
   * @param offsetInVersion0 offset in the original document
   * @return corresponding offset in the current document
   */
  int toCurrentOffset(int offsetInVersion0);

  /**
   * Maps an offset from the current version back to version 0.
   * <p>
   * If the current offset belongs to surviving original text, returns the
   * corresponding original offset.
   * <p>
   * If the current offset lies inside inserted text, returns the version-0
   * boundary after that inserted gap.
   *
   * @param offsetInCurrent offset in the current document
   * @return corresponding offset in version 0
   */
  int toVersion0Offset(int offsetInCurrent);

  /**
   * Returns a new tree representing insertion of {@code length} characters
   * at {@code offsetInCurrent}.
   * <p>
   * The current tree remains unchanged.
   *
   * @param offsetInCurrent insertion offset in the current document
   * @param length          inserted text length, must be >= 0
   * @return new persistent modification tree
   */
  @NotNull ModificationTree insert(int offsetInCurrent, int length);

  /**
   * Returns a new tree representing deletion of current-version text in
   * {@code [startInCurrent, endInCurrent)}.
   * <p>
   * The current tree remains unchanged.
   *
   * @param startInCurrent deletion start offset in the current document
   * @param endInCurrent   deletion end offset in the current document
   * @return new persistent modification tree
   */
  @NotNull ModificationTree delete(int startInCurrent, int endInCurrent);

  void checkInvariants();
}