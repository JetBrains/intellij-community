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
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPaar;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.List;


public class FieldExprent extends Exprent {

  private String name;

  private String classname;

  private boolean isStatic;

  private Exprent instance;

  private FieldDescriptor descriptor;

  {
    this.type = EXPRENT_FIELD;
  }

  public FieldExprent(LinkConstant cn, Exprent instance) {

    this.instance = instance;

    if (instance == null) {
      isStatic = true;
    }

    classname = cn.classname;
    name = cn.elementname;
    descriptor = FieldDescriptor.parseDescriptor(cn.descriptor);
  }

  public FieldExprent(String name, String classname, boolean isStatic, Exprent instance, FieldDescriptor descriptor) {
    this.name = name;
    this.classname = classname;
    this.isStatic = isStatic;
    this.instance = instance;
    this.descriptor = descriptor;
  }

  public VarType getExprType() {
    return descriptor.type;
  }

  public int getExprentUse() {
    if (instance == null) {
      return Exprent.MULTIPLE_USES;
    }
    else {
      return instance.getExprentUse() & Exprent.MULTIPLE_USES;
    }
  }

  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<Exprent>();
    if (instance != null) {
      lst.add(instance);
    }
    return lst;
  }

  public Exprent copy() {
    return new FieldExprent(name, classname, isStatic, instance == null ? null : instance.copy(), descriptor);
  }

  public String toJava(int indent) {
    StringBuilder buf = new StringBuilder();


    if (isStatic) {
      ClassNode node = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
      if (node == null || !classname.equals(node.classStruct.qualifiedName)) {
        buf.append(DecompilerContext.getImportCollector().getShortName(ExprProcessor.buildJavaClassName(classname)));
        buf.append(".");
      }
    }
    else {

      String super_qualifier = null;

      if (instance != null && instance.type == Exprent.EXPRENT_VAR) {
        VarExprent instvar = (VarExprent)instance;
        VarVersionPaar varpaar = new VarVersionPaar(instvar);

        MethodWrapper current_meth = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);

        if (current_meth != null) { // FIXME: remove
          String this_classname = current_meth.varproc.getThisvars().get(varpaar);

          if (this_classname != null) {
            if (!classname.equals(this_classname)) { // TODO: direct comparison to the super class?
              super_qualifier = this_classname;
            }
          }
        }
      }

      if (super_qualifier != null) {
        StructClass current_class = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE)).classStruct;

        if (!super_qualifier.equals(current_class.qualifiedName)) {
          buf.append(DecompilerContext.getImportCollector().getShortName(ExprProcessor.buildJavaClassName(super_qualifier)));
          buf.append(".");
        }
        buf.append("super");
      }
      else {
        StringBuilder buff = new StringBuilder();
        boolean casted = ExprProcessor.getCastedExprent(instance, new VarType(CodeConstants.TYPE_OBJECT, 0, classname), buff, indent, true);
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

    return buf.toString();
  }

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

  public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
    if (oldexpr == instance) {
      instance = newexpr;
    }
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
}
