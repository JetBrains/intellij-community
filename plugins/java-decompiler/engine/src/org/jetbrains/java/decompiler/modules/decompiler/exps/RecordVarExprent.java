// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents record deconstruction
 */
public class RecordVarExprent extends VarExprent {

  @NotNull
  private final List<RecordVarExprent> components = new ArrayList<>();

  public RecordVarExprent(VarExprent v) {
    super(v.getIndex(), v.getVarType(), v.getProcessor());
    if (v.isClassDef()) {
      throw new UnsupportedOperationException("Expect only var definition");
    }
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    if (components.isEmpty()) {
      setDefinition(true);
      return super.toJava(indent, tracer);
    }
    TextBuffer buffer = new TextBuffer();

    appendDefinitionType(buffer);

    buffer.append("(");
    for (int i = 0; i < components.size(); i++) {
      buffer.append(components.get(i).toJava(0, tracer));
      if (i != components.size() - 1) {
        buffer.append(", ");
      }
    }
    buffer.append(")");
    return buffer;
  }

  public void addComponent(@NotNull RecordVarExprent exprent) {
    components.add(exprent);
  }

  /**
   * @return a copy of the current RecordVarExprent object, including a copy of its VarExprent superclass and components
   */
  @Override
  public RecordVarExprent copy() {
    VarExprent copy = (VarExprent)super.copy();
    RecordVarExprent newRoot = new RecordVarExprent(copy);
    for (RecordVarExprent component : components) {
      newRoot.addComponent(component.copy());
    }
    return newRoot;
  }

  /**
   * Copies the values from the provided VarExprent object to the current RecordVarExprent object.
   * If the provided VarExprent object is an instance of RecordVarExprent, copies the components from it.
   * Otherwise, copy the varType, index, and version from the provided VarExprent object.
   *
   * @param varExprent the VarExprent object to copy the values from
   * @return true if the copying is successful (the current set of components is empty), false otherwise
   */
  public boolean copyFrom(@NotNull VarExprent varExprent) {
    if (varExprent instanceof RecordVarExprent recordVarExprent) {
      if (!this.components.isEmpty()) {
        return false;
      }
      this.components.addAll(recordVarExprent.components);
    }
    this.setVarType(varExprent.getVarType());
    this.setIndex(varExprent.getIndex());
    this.setVersion(varExprent.getVersion());
    return true;
  }

  public List<RecordVarExprent> getComponents() {
    return new ArrayList<>(components);
  }

  /**
   * Retrieves the direct component of a RecordVarExprent that equals the provided Exprent.
   *
   * @param exprent the Exprent to match
   * @return the direct component of the RecordVarExprent that matches the provided Exprent, or null if no match is found
   */
  @Nullable
  public RecordVarExprent getDirectComponent(@Nullable Exprent exprent) {
    for (RecordVarExprent component : components) {
      if (component.equals(exprent)) {
        return component;
      }
    }
    return null;
  }
}
