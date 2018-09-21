// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

public class PassThrough extends TreeUiTestCase {
  public PassThrough() {
    super(true);
  }

  @Override
  public void testSelectionGoesToParentWhenOnlyChildMoved2() {
    //todo
  }

  @Override
  public void testQueryStructureWhenExpand() {
    //todo
  }

  @Override
  public void testMoveElementToAdjacentEmptyParentWithSmartExpandAndSerialUpdateSubtrees() {
    // doesn't make sense since pass-through mode is always serial, it doesn't queue for updates
  }

  @Override
  public void testElementMove1() {
    //todo
  }

  @Override
  public void testClear() {
    //todo
  }

  @Override
  public void testDoubleCancelUpdate() {
    // doesn't make sense in pass-through mode
  }

  @Override
  public void testNoExtraJTreeModelUpdate() {
    // doesn't make sense in pass-through mode
  }

  @Override
  public void testSelectWhenUpdatesArePending() {
    // doesn't make sense in pass-through mode
  }

  @Override
  public void testBigTreeUpdate() {
    // doesn't make sense in pass-through mode
  }
}
