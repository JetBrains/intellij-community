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
