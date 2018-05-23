/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.usages.UsageView;
import com.intellij.util.BitUtil;
import com.intellij.util.Consumer;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Vector;

/**
 * @author max
 */
public abstract class Node extends DefaultMutableTreeNode {
  private int myCachedTextHash;

  private byte myCachedFlags; // guarded by this; bit packed flags below:
  static final byte EXCLUDED_MASK = 1<<3;
  private static final byte UPDATED_MASK = 1<<4;

  private static final byte CACHED_INVALID_MASK = 1;
  private static final byte CACHED_READ_ONLY_MASK = 1 << 1;
  private static final byte READ_ONLY_COMPUTED_MASK = 1<<2;

  @MagicConstant(intValues = {CACHED_INVALID_MASK, CACHED_READ_ONLY_MASK, READ_ONLY_COMPUTED_MASK, EXCLUDED_MASK, UPDATED_MASK})
  private @interface FlagConstant {}

  private synchronized boolean isFlagSet(@FlagConstant byte mask) {
    return BitUtil.isSet(myCachedFlags, mask);
  }

  private synchronized void setFlag(@FlagConstant byte mask, boolean value) {
    myCachedFlags = BitUtil.set(myCachedFlags, mask, value);
  }

  Node() {
  }

  /**
   * debug method for producing string tree presentation
   */
  public abstract String tree2string(int indent, String lineSeparator);

  /**
   * isDataXXX methods perform actual (expensive) data computation.
   * Called from {@link #update(UsageView, Consumer)})
   * to be compared later with cached data stored in {@link #myCachedFlags} and {@link #myCachedTextHash}
   */
  protected abstract boolean isDataValid();
  protected abstract boolean isDataReadOnly();
  protected abstract boolean isDataExcluded();

  @NotNull
  protected abstract String getText(@NotNull UsageView view);

  public final boolean isValid() {
    return !isFlagSet(CACHED_INVALID_MASK);
  }

  public final boolean isReadOnly() {
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

  public final boolean isExcluded() {
    return isFlagSet(EXCLUDED_MASK);
  }

  final synchronized void update(@NotNull UsageView view, @NotNull Consumer<? super Node> edtNodeChangedQueue) {
    // performance: always update in background because smart pointer' isValid() can cause PSI chameleons expansion which is ridiculously expensive in cpp
    assert !ApplicationManager.getApplication().isDispatchThread();
    boolean isDataValid = isDataValid();
    boolean isReadOnly = isDataReadOnly();
    String text = getText(view);

    boolean cachedValid = isValid();
    boolean cachedReadOnly = isFlagSet(CACHED_READ_ONLY_MASK);

    if (isDataValid != cachedValid ||
        isReadOnly != cachedReadOnly ||
        myCachedTextHash != text.hashCode()) {
      setFlag(CACHED_INVALID_MASK, !isDataValid);
      setFlag(CACHED_READ_ONLY_MASK, isReadOnly);

      myCachedTextHash = text.hashCode();
      updateNotify();
      edtNodeChangedQueue.consume(this);
    }
    setFlag(UPDATED_MASK, true);
  }

  void markNeedUpdate() {
    setFlag(UPDATED_MASK, false);
  }
  boolean needsUpdate() {
    return !isFlagSet(UPDATED_MASK);
  }

  /**
   * Override to perform node-specific updates 
   */
  protected void updateNotify() {
  }

  // same as DefaultMutableTreeNode.insert() except it doesn't try to remove the newChild from its parent since we know it's new
  void insertNewNode(@NotNull Node newChild, int childIndex) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (children == null) {
      children = new Vector();
    }
    //noinspection unchecked
    children.insertElementAt(newChild, childIndex);
  }

  void setExcluded(boolean excluded, @NotNull Consumer<? super Node> edtNodeChangedQueue) {
    setFlag(EXCLUDED_MASK, excluded);
    edtNodeChangedQueue.consume(this);
  }
}
