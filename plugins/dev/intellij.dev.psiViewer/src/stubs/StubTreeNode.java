// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer.stubs;

import com.intellij.psi.stubs.StubElement;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin.Ulitin
 */
public class StubTreeNode extends SimpleNode {
  
  private final @NotNull StubElement<?> myStub;

  public StubTreeNode(@NotNull StubElement<?> stub, StubTreeNode parent) {
    super(parent);
    myStub = stub;
  }

  public @NotNull StubElement<?> getStub() {
    return myStub;
  }

  @Override
  public StubTreeNode @NotNull [] getChildren() {
    return ContainerUtil.map2Array(myStub.getChildrenStubs(), StubTreeNode.class, stub -> new StubTreeNode(stub, this));
  }

  @Override
  public Object @NotNull [] getEqualityObjects() {
    return new Object[]{myStub};
  }

  @Override
  public String getName() {
    return myStub.toString();
  }
}
