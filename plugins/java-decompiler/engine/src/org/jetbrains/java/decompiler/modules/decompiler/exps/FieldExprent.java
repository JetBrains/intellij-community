/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FieldExprent extends Exprent {
  private final String name;
  private final String classname;
  private final boolean isStatic;
  private Exprent instance;
  private final FieldDescriptor descriptor;

  public FieldExprent(LinkConstant cn, Exprent instance, Set<Integer> bytecodeOffsets) {
    this(cn.elementname, cn.classname, instance == null, instance, FieldDescriptor.parseDescriptor(cn.descriptor), bytecodeOffsets);
  }

  public FieldExprent(String name, String classname, boolean isStatic, Exprent instance, FieldDescriptor descriptor, Set<Integer> bytecodeOffsets) {
    super(EXPRENT_FIELD);
    this.name = name;
    this.classname = classname;
    this.isStatic = isStatic;
    this.instance = instance;
    this.descriptor = descriptor;

    addBytecodeOffsets(bytecodeOffsets);
  }

  @Override
  public VarType getExprType() {
    return descriptor.type;
  }

  @Override
  public int getExprentUse() {
    return instance == null ? Exprent.MULTIPLE_USES : instance.getExprentUse() & Exprent.MULTIPLE_USES;
  }

  @Override
  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<>();
    if (instance != null) {
      lst.add(instance);
    }
    return lst;
  }

  @Override
  public Exprent copy() {
    return new FieldExprent(name, classname, isStatic, instance == null ? null : instance.copy(), descriptor, bytecode);
  }

  private boolean isAmbiguous() {
    MethodWrapper method = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
    if (method != null) {
      StructLocalVariableTableAttribute attr = method.methodStruct.getLocalVariableAttr();
      if (attr != null) {
        return attr.getMapVarNames().containsValue(name);
      }
    }

    return false;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buf = new TextBuffer();

    if (isStatic) {
      ClassNode node = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
      if (node == null || !classname.equals(node.classStruct.qualifiedName) || isAmbiguous()) {
        buf.append(DecompilerContext.getImportCollector().getShortName(ExprProcessor.buildJavaClassName(classname)));
        buf.append(".");
      }
    }
    else {
      String super_qualifier = null;

      if (instance != null && instance.type == Exprent.EXPRENT_VAR) {
        VarExprent instVar = (VarExprent)instance;
        VarVersionPair pair = new VarVersionPair(instVar);

        MethodWrapper currentMethod = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);

        if (currentMethod != null) { // FIXME: remove
          String this_classname = currentMethod.varproc.getThisVars().get(pair);

          if (this_classname != null) {
            if (!classname.equals(this_classname)) { // TODO: direct comparison to the super class?
              super_qualifier = this_classname;
            }
          }
        }
      }

      if (super_qualifier != null) {
        TextUtil.writeQualifiedSuper(buf, super_qualifier);
      }
      else {
        TextBuffer buff = new TextBuffer();
        boolean casted = ExprProcessor.getCastedExprent(instance, new VarType(CodeConstants.TYPE_OBJECT, 0, classname), buff, indent, true, tracer);
        String res = buff.toString();

        if (casted || instance.getPrecedence() > getPrecedence()) {
          res = "(" + res + ")";
        }

        buf.append(res);
      }

      if (buf.toString().equals(
        VarExprent.VAR_NAMELESS_ENCLOSURE)) { // FIXME: workaround for field access of an anonymous enclosing class. Find a better way.
        buf.setLength(0);
      }
      else {
        buf.append(".");
      }
    }

    buf.append(name);

    tracer.addMapping(bytecode);

    return buf;
  }

  @Override
  public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
    if (oldExpr == instance) {
      instance = newExpr;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof FieldExprent)) return false;

    FieldExprent ft = (FieldExprent)o;
    return InterpreterUtil.equalObjects(name, ft.getName()) &&
           InterpreterUtil.equalObjects(classname, ft.getClassname()) &&
           isStatic == ft.isStatic() &&
           InterpreterUtil.equalObjects(instance, ft.getInstance()) &&
           InterpreterUtil.equalObjects(descriptor, ft.getDescriptor());
  }

  public String getClassname() {
    return classname;
  }

  public FieldDescriptor getDescriptor() {
    return descriptor;
  }

  public Exprent getInstance() {
    return instance;
  }

  public boolean isStatic() {
    return isStatic;
  }

  public String getName() {
    return name;
  }
  
  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************
  
  public boolean match(MatchNode matchNode, MatchEngine engine) {

    if(!super.match(matchNode, engine)) {
      return false;
    }
    
    RuleValue rule = matchNode.getRules().get(MatchProperties.EXPRENT_FIELD_NAME);
    if(rule != null) {
      if(rule.isVariable()) {
        if(!engine.checkAndSetVariableValue((String)rule.value, this.name)) {
          return false;
        }
      } else { 
        if(!rule.value.equals(this.name)) {
          return false;
        }
      }
    }
    
    return true;
  }
}