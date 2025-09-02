// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.usages.UsageNodePresentation;
import com.intellij.util.BitUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Vector;

@ApiStatus.Internal
public abstract class Node extends DefaultMutableTreeNode {
  private int myCachedTextHash;

  private byte myCachedFlags; // guarded by this; bit packed flags below:

  private static final byte CACHED_INVALID_MASK = 1;
  private static final byte CACHED_READ_ONLY_MASK = 1 << 1;
  private static final byte READ_ONLY_COMPUTED_MASK = 1 << 2;
  static final byte EXCLUDED_MASK = 1 << 3;
  private static final byte UPDATED_MASK = 1 << 4;
  private static final byte FORCE_UPDATE_REQUESTED_MASK = 1 << 5;

  @MagicConstant(intValues = {
    CACHED_INVALID_MASK,
    CACHED_READ_ONLY_MASK,
    READ_ONLY_COMPUTED_MASK,
    EXCLUDED_MASK,
    UPDATED_MASK,
    FORCE_UPDATE_REQUESTED_MASK,
  })
  private @interface FlagConstant {
  }

  private synchronized boolean isFlagSet(@FlagConstant byte mask) {
    return BitUtil.isSet(myCachedFlags, mask);
  }

  private synchronized void setFlag(@FlagConstant byte mask, boolean value) {
    myCachedFlags = BitUtil.set(myCachedFlags, mask, value);
  }

  Node() {
  }

  /**
   * isDataXXX methods perform actual (expensive) data computation.
   * Called from {@link #update(Consumer)}
   * to be compared later with cached data stored in {@link #myCachedFlags} and {@link #myCachedTextHash}
   */
  protected abstract boolean isDataValid();

  protected abstract boolean isDataReadOnly();

  protected abstract boolean isDataExcluded();

  @ApiStatus.Internal
  public @Nullable UsageNodePresentation getCachedPresentation() {
    return null;
  }

  protected void updateCachedPresentation() { }

  protected abstract @NotNull String getNodeText();

  final boolean isValid() {
    return !isFlagSet(CACHED_INVALID_MASK);
  }

  final boolean isReadOnly() {
    boolean result;
    boolean computed = isFlagSet(READ_ONLY_COMPUTED_MASK);
    if (computed) {
      result = isFlagSet(CACHED_READ_ONLY_MASK);
    }
    else {
      result = isDataReadOnly();
      setFlag(READ_ONLY_COMPUTED_MASK, true);
      setFlag(CACHED_READ_ONLY_MASK, result);
    }
    return result;
  }

  @VisibleForTesting
  public final boolean isExcluded() {
    return isFlagSet(EXCLUDED_MASK);
  }

  final void update(@NotNull Consumer<? super Node> edtFireTreeNodesChangedQueue) {
    // performance: always update in background because smart pointer' isValid() can cause PSI chameleons expansion which is ridiculously expensive in cpp
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    boolean isDataValid = isDataValid();
    boolean isReadOnly = isDataReadOnly();
    String text = getNodeText();
    updateCachedPresentation();
    doUpdate(isDataValid, isReadOnly, text, edtFireTreeNodesChangedQueue);
  }

  private synchronized void doUpdate(boolean isDataValid,
                                     boolean isReadOnly,
                                     @NotNull String text,
                                     @NotNull Consumer<? super Node> edtFireTreeNodesChangedQueue) {
    boolean cachedValid = isValid();
    boolean cachedReadOnly = isFlagSet(CACHED_READ_ONLY_MASK);

    if (isDataValid != cachedValid ||
        isReadOnly != cachedReadOnly ||
        myCachedTextHash != text.hashCode() ||
        isFlagSet(FORCE_UPDATE_REQUESTED_MASK)) {
      setFlag(CACHED_INVALID_MASK, !isDataValid);
      setFlag(CACHED_READ_ONLY_MASK, isReadOnly);
      setFlag(FORCE_UPDATE_REQUESTED_MASK, false);

      myCachedTextHash = text.hashCode();
      updateNotify();
      edtFireTreeNodesChangedQueue.consume(this);
    }
    setFlag(UPDATED_MASK, true);
  }

  void markNeedUpdate() {
    setFlag(UPDATED_MASK, false);
  }

  boolean needsUpdate() {
    return !isFlagSet(UPDATED_MASK);
  }

  void forceUpdate() {
    setFlag(FORCE_UPDATE_REQUESTED_MASK, true);
  }

  /**
   * Override to perform node-specific updates
   */
  protected void updateNotify() {
  }

  // same as DefaultMutableTreeNode.insert() except it doesn't try to remove the newChild from its parent since we know it's new
  void insertNewNode(@NotNull Node newChild, int childIndex) {
    ThreadingAssertions.assertEventDispatchThread();
    if (children == null) {
      //obsolete type is required by platform superclass
      //noinspection UseOfObsoleteCollectionType
      children = new Vector<>();
    }
    children.insertElementAt(newChild, childIndex);
  }

  void setExcluded(boolean excluded, @NotNull Consumer<? super Node> edtFireTreeNodesChangedQueue) {
    setFlag(EXCLUDED_MASK, excluded);
    edtFireTreeNodesChangedQueue.consume(this);
  }
}
