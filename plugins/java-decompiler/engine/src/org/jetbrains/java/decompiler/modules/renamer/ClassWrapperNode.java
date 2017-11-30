// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.renamer;

import org.jetbrains.java.decompiler.struct.StructClass;

import java.util.ArrayList;
import java.util.List;

public class ClassWrapperNode {

  private final StructClass classStruct;

  private ClassWrapperNode superclass;

  private final List<ClassWrapperNode> subclasses = new ArrayList<>();

  public ClassWrapperNode(StructClass cl) {
    this.classStruct = cl;
  }

  public void addSubclass(ClassWrapperNode node) {
    node.setSuperclass(this);
    subclasses.add(node);
  }

  public StructClass getClassStruct() {
    return classStruct;
  }

  public List<ClassWrapperNode> getSubclasses() {
    return subclasses;
  }

  public ClassWrapperNode getSuperclass() {
    return superclass;
  }

  public void setSuperclass(ClassWrapperNode superclass) {
    this.superclass = superclass;
  }
}
