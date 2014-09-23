/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassWriter;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarTypeProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPaar;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.List;

public class VarExprent extends Exprent {

  public static final int STACK_BASE = 10000;

  public static final String VAR_NAMELESS_ENCLOSURE = "<VAR_NAMELESS_ENCLOSURE>";

  private int index;

  private VarType vartype;

  private boolean definition = false;

  private VarProcessor processor;

  private int version = 0;

  private boolean classdef = false;

  private boolean stack = false;

  {
    this.type = EXPRENT_VAR;
  }

  public VarExprent(int index, VarType vartype, VarProcessor processor) {
    this.index = index;
    this.vartype = vartype;
    this.processor = processor;
  }

  public VarType getExprType() {
    return getVartype();
  }

  public int getExprentUse() {
    return Exprent.MULTIPLE_USES | Exprent.SIDE_EFFECTS_FREE;
  }

  public List<Exprent> getAllExprents() {
    return new ArrayList<Exprent>();
  }

  public Exprent copy() {
    VarExprent var = new VarExprent(index, getVartype(), processor);
    var.setDefinition(definition);
    var.setVersion(version);
    var.setClassdef(classdef);
    var.setStack(stack);
    return var;
  }

  public String toJava(int indent) {
    StringBuilder buffer = new StringBuilder();

    if (classdef) {
      ClassNode child = DecompilerContext.getClassProcessor().getMapRootClasses().get(vartype.value);
      new ClassWriter().classToJava(child, buffer, indent);
    }
    else {
      String name = null;
      if (processor != null) {
        name = processor.getVarName(new VarVersionPaar(index, version));
      }

      if (definition) {
        if (processor != null && processor.getVarFinal(new VarVersionPaar(index, version)) == VarTypeProcessor.VAR_FINALEXPLICIT) {
          buffer.append("final ");
        }
        buffer.append(ExprProcessor.getCastTypeName(getVartype())).append(" ");
      }
      buffer.append(name == null ? ("var" + index + (version == 0 ? "" : "_" + version)) : name);
    }

    return buffer.toString();
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof VarExprent)) return false;

    VarExprent ve = (VarExprent)o;
    return index == ve.getIndex() &&
           version == ve.getVersion() &&
           InterpreterUtil.equalObjects(getVartype(), ve.getVartype()); // FIXME: vartype comparison redundant?
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public VarType getVartype() {
    VarType vt = null;
    if (processor != null) {
      vt = processor.getVarType(new VarVersionPaar(index, version));
    }

    if (vt == null || (vartype != null && vartype.type != CodeConstants.TYPE_UNKNOWN)) {
      vt = vartype;
    }

    return vt == null ? VarType.VARTYPE_UNKNOWN : vt;
  }

  public void setVartype(VarType vartype) {
    this.vartype = vartype;
  }

  public boolean isDefinition() {
    return definition;
  }

  public void setDefinition(boolean definition) {
    this.definition = definition;
  }

  public VarProcessor getProcessor() {
    return processor;
  }

  public void setProcessor(VarProcessor processor) {
    this.processor = processor;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public boolean isClassdef() {
    return classdef;
  }

  public void setClassdef(boolean classdef) {
    this.classdef = classdef;
  }

  public boolean isStack() {
    return stack;
  }

  public void setStack(boolean stack) {
    this.stack = stack;
  }
}
