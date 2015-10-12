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

import com.intellij.openapi.util.Comparing;
import com.intellij.usages.UsageView;
import com.intellij.util.BitUtil;
import com.intellij.util.Consumer;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author max
 */
public abstract class Node extends DefaultMutableTreeNode {
  protected final DefaultTreeModel myTreeModel;
  private String myCachedText;

  private byte myCachedFlags; // bit packed flags below:
  private static final byte INVALID_MASK = 1;
  private static final byte READ_ONLY_MASK = 2;
  private static final byte READ_ONLY_COMPUTED_MASK = 4;
  private static final byte EXCLUDED_MASK = 8;
  private static final byte UPDATED_MASK = 16;

  @MagicConstant(intValues = {INVALID_MASK, READ_ONLY_MASK, READ_ONLY_COMPUTED_MASK, EXCLUDED_MASK, UPDATED_MASK})
  private @interface FlagConstant {}

  private boolean isFlagSet(@FlagConstant byte mask) {
    return BitUtil.isSet(myCachedFlags, mask);
  }

  private void setFlag(@FlagConstant byte mask, boolean value) {
    myCachedFlags = BitUtil.set(myCachedFlags, mask, value);
  }

  protected Node(@NotNull DefaultTreeModel model) {
    myTreeModel = model;
  }

  /**
   * debug method for producing string tree presentation
   */
  public abstract String tree2string(int indent, String lineSeparator);

  /**
   * isDataXXX methods perform actual (expensive) data computation.
   * Called from  {@link #update(UsageView, Consumer)})
   * to be compared later with cached data stored in {@link #myCachedFlags} and {@link #myCachedText}
   */
  protected abstract boolean isDataValid();
  protected abstract boolean isDataReadOnly();
  protected abstract boolean isDataExcluded();


  protected abstract String getText(@NotNull UsageView view);

  public final boolean isValid() {
    return !isFlagSet(INVALID_MASK);
  }

  public final boolean isReadOnly() {
    boolean result;
    boolean computed = isFlagSet(READ_ONLY_COMPUTED_MASK);
    if (computed) {
      result = isFlagSet(READ_ONLY_MASK);
    }
    else {
      result = isDataReadOnly();
      setFlag(READ_ONLY_COMPUTED_MASK, true);
      setFlag(READ_ONLY_MASK, result);
    }
    return result;
  }

  public final boolean isExcluded() {
    return isFlagSet(EXCLUDED_MASK);
  }

  final void update(@NotNull UsageView view, @NotNull Consumer<Runnable> edtQueue) {
    boolean isDataValid = isDataValid();
    boolean isReadOnly = isDataReadOnly();
    boolean isExcluded = isDataExcluded();
    String text = getText(view);

    boolean cachedValid = isValid();
    boolean cachedReadOnly = isFlagSet(READ_ONLY_MASK);
    boolean cachedExcluded = isFlagSet(EXCLUDED_MASK);

    if (isDataValid != cachedValid || isReadOnly != cachedReadOnly || isExcluded != cachedExcluded || !Comparing.equal(myCachedText, text)) {
      setFlag(INVALID_MASK, !isDataValid);
      setFlag(READ_ONLY_MASK, isReadOnly);
      setFlag(EXCLUDED_MASK, isExcluded);

      myCachedText = text;
      updateNotify();
      edtQueue.consume(new Runnable() {
        @Override
        public void run() {
          myTreeModel.nodeChanged(Node.this);
        }
      });
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
}
