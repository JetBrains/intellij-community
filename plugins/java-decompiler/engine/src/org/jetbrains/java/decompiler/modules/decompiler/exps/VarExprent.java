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
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarTypeProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.IMatchable.MatchProperties;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.List;

public class VarExprent extends Exprent {

  public static final int STACK_BASE = 10000;
  public static final String VAR_NAMELESS_ENCLOSURE = "<VAR_NAMELESS_ENCLOSURE>";

  private int index;
  private VarType varType;
  private boolean definition = false;
  private VarProcessor processor;
  private int version = 0;
  private boolean classDef = false;
  private boolean stack = false;

  public VarExprent(int index, VarType varType, VarProcessor processor) {
    super(EXPRENT_VAR);
    this.index = index;
    this.varType = varType;
    this.processor = processor;
  }

  @Override
  public VarType getExprType() {
    return getVarType();
  }

  @Override
  public int getExprentUse() {
    return Exprent.MULTIPLE_USES | Exprent.SIDE_EFFECTS_FREE;
  }

  @Override
  public List<Exprent> getAllExprents() {
    return new ArrayList<>();
  }

  @Override
  public Exprent copy() {
    VarExprent var = new VarExprent(index, getVarType(), processor);
    var.setDefinition(definition);
    var.setVersion(version);
    var.setClassDef(classDef);
    var.setStack(stack);
    return var;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buffer = new TextBuffer();

    tracer.addMapping(bytecode);

    if (classDef) {
      ClassNode child = DecompilerContext.getClassProcessor().getMapRootClasses().get(varType.value);
      new ClassWriter().classToJava(child, buffer, indent, tracer);
      tracer.incrementCurrentSourceLine(buffer.countLines());
    }
    else {
      String name = null;
      if (processor != null) {
        name = processor.getVarName(new VarVersionPair(index, version));
      }

      if (definition) {
        if (processor != null && processor.getVarFinal(new VarVersionPair(index, version)) == VarTypeProcessor.VAR_EXPLICIT_FINAL) {
          buffer.append("final ");
        }
        buffer.append(ExprProcessor.getCastTypeName(getVarType())).append(" ");
      }
      buffer.append(name == null ? ("var" + index + (version == 0 ? "" : "_" + version)) : name);
    }

    return buffer;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof VarExprent)) return false;

    VarExprent ve = (VarExprent)o;
    return index == ve.getIndex() &&
           version == ve.getVersion() &&
           InterpreterUtil.equalObjects(getVarType(), ve.getVarType()); // FIXME: varType comparison redundant?
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public VarType getVarType() {
    VarType vt = null;
    if (processor != null) {
      vt = processor.getVarType(new VarVersionPair(index, version));
    }

    if (vt == null || (varType != null && varType.type != CodeConstants.TYPE_UNKNOWN)) {
      vt = varType;
    }

    return vt == null ? VarType.VARTYPE_UNKNOWN : vt;
  }

  public void setVarType(VarType varType) {
    this.varType = varType;
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

  public boolean isClassDef() {
    return classDef;
  }

  public void setClassDef(boolean classDef) {
    this.classDef = classDef;
  }

  public boolean isStack() {
    return stack;
  }

  public void setStack(boolean stack) {
    this.stack = stack;
  }
  
  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************
  
  public boolean match(MatchNode matchNode, MatchEngine engine) {

    if(!super.match(matchNode, engine)) {
      return false;
    }
    
    RuleValue rule = matchNode.getRules().get(MatchProperties.EXPRENT_VAR_INDEX);
    if(rule != null) {
      if(rule.isVariable()) {
        if(!engine.checkAndSetVariableValue((String)rule.value, this.index)) {
          return false;
        }
      } else { 
        if(this.index != Integer.valueOf((String)rule.value).intValue()) {
          return false;
        }
      }
    }
    
    return true;
  }
  
}
