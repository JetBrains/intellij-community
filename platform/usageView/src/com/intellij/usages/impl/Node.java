/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
  private static final int INVALID_FLAG = 0;
  private static final int READ_ONLY_FLAG = 1;
  private static final int READ_ONLY_COMPUTED_FLAG = 2;
  private static final int EXCLUDED_FLAG = 3;
  private static final int UPDATED_FLAG = 4;

  @MagicConstant(intValues = {INVALID_FLAG, READ_ONLY_FLAG, READ_ONLY_COMPUTED_FLAG, EXCLUDED_FLAG, UPDATED_FLAG})
  @interface FlagConstant {}

  private boolean isFlagSet(@FlagConstant int flag) {
    int state = myCachedFlags >> flag;
    return (state & 1) != 0;
  }

  private void setFlag(@FlagConstant int flag, boolean value) {
    int state = value ? 1 : 0;
    myCachedFlags = (byte)(myCachedFlags & ~(1 << flag) | state << flag);
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
   * Called from  {@link #update(com.intellij.usages.UsageView)})
   * to be compared later with cached data stored in {@link #myCachedFlags} and {@link #myCachedText}
   */
  protected abstract boolean isDataValid();
  protected abstract boolean isDataReadOnly();
  protected abstract boolean isDataExcluded();


  protected abstract String getText(@NotNull UsageView view);

  public final boolean isValid() {
    return !isFlagSet(INVALID_FLAG);
  }

  public final boolean isReadOnly() {
    boolean result;
    boolean computed = isFlagSet(READ_ONLY_COMPUTED_FLAG);
    if (computed) {
      result = isFlagSet(READ_ONLY_FLAG);
    }
    else {
      result = isDataReadOnly();
      setFlag(READ_ONLY_COMPUTED_FLAG, true);
      setFlag(READ_ONLY_FLAG, result);
    }
    return result;
  }

  public final boolean isExcluded() {
    return isFlagSet(EXCLUDED_FLAG);
  }

  public final void update(@NotNull UsageView view) {
    boolean isDataValid = isDataValid();
    boolean isReadOnly = isDataReadOnly();
    boolean isExcluded = isDataExcluded();
    String text = getText(view);

    boolean cachedValid = isValid();
    boolean cachedReadOnly = isFlagSet(READ_ONLY_FLAG);
    boolean cachedExcluded = isFlagSet(EXCLUDED_FLAG);

    if (isDataValid != cachedValid || isReadOnly != cachedReadOnly || isExcluded != cachedExcluded || !Comparing.equal(myCachedText, text)) {
      setFlag(INVALID_FLAG, !isDataValid);
      setFlag(READ_ONLY_FLAG, isReadOnly);
      setFlag(EXCLUDED_FLAG, isExcluded);

      myCachedText = text;
      updateNotify();
      myTreeModel.nodeChanged(this);
    }
    setFlag(UPDATED_FLAG, true);
  }

  public void markNeedUpdate() {
    setFlag(UPDATED_FLAG, false);
  }
  public boolean needsUpdate() {
    return !isFlagSet(UPDATED_FLAG);
  }

  /**
   * Override to perform node-specific updates 
   */
  protected void updateNotify() {
  }
}
