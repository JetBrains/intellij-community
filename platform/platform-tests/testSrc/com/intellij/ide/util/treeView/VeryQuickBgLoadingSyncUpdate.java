// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

public abstract class VeryQuickBgLoadingSyncUpdate extends TreeUiTestCase {
  public VeryQuickBgLoadingSyncUpdate() {
    super(false, true);
  }

  @Override
  public void testNoInfiniteSmartExpand() {
    // todo;
  }

  @Override
  public void testReleaseBuilderDuringUpdate() {
    // todo
  }

  @Override
  public void testReleaseBuilderDuringGetChildren() {
    // todo
  }
}
