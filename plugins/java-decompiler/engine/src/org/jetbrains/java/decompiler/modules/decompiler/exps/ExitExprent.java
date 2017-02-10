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
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.struct.attr.StructExceptionsAttribute;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExitExprent extends Exprent {

  public static final int EXIT_RETURN = 0;
  public static final int EXIT_THROW = 1;

  private final int exitType;
  private Exprent value;
  private final VarType retType;

  public ExitExprent(int exitType, Exprent value, VarType retType, Set<Integer> bytecodeOffsets) {
    super(EXPRENT_EXIT);
    this.exitType = exitType;
    this.value = value;
    this.retType = retType;

    addBytecodeOffsets(bytecodeOffsets);
  }

  @Override
  public Exprent copy() {
    return new ExitExprent(exitType, value == null ? null : value.copy(), retType, bytecode);
  }

  @Override
  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    if (exitType == EXIT_RETURN && retType.type != CodeConstants.TYPE_VOID) {
      result.addMinTypeExprent(value, VarType.getMinTypeInFamily(retType.typeFamily));
      result.addMaxTypeExprent(value, retType);
    }

    return result;
  }

  @Override
  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<>();
    if (value != null) {
      lst.add(value);
    }
    return lst;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    tracer.addMapping(bytecode);

    if (exitType == EXIT_RETURN) {
      TextBuffer buffer = new TextBuffer("return");

      if (retType.type != CodeConstants.TYPE_VOID) {
        buffer.append(' ');
        ExprProcessor.getCastedExprent(value, retType, buffer, indent, false, tracer);
      }

      return buffer;
    }
    else {
      MethodWrapper method = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
      ClassNode node = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE));

      if (method != null && node != null) {
        StructExceptionsAttribute attr = (StructExceptionsAttribute)method.methodStruct.getAttributes().getWithKey("Exceptions");

        if (attr != null) {
          String classname = null;

          for (int i = 0; i < attr.getThrowsExceptions().size(); i++) {
            String exClassName = attr.getExcClassname(i, node.classStruct.getPool());
            if ("java/lang/Throwable".equals(exClassName)) {
              classname = exClassName;
              break;
            }
            else if ("java/lang/Exception".equals(exClassName)) {
              classname = exClassName;
            }
          }

          if (classname != null) {
            VarType exType = new VarType(classname, true);
            TextBuffer buffer = new TextBuffer("throw ");
            ExprProcessor.getCastedExprent(value, exType, buffer, indent, false, tracer);
            return buffer;
          }
        }
      }

      return value.toJava(indent, tracer).prepend("throw ");
    }
  }

  @Override
  public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
    if (oldExpr == value) {
      value = newExpr;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof ExitExprent)) return false;

    ExitExprent et = (ExitExprent)o;
    return exitType == et.getExitType() &&
           InterpreterUtil.equalObjects(value, et.getValue());
  }

  public int getExitType() {
    return exitType;
  }

  public Exprent getValue() {
    return value;
  }

  public VarType getRetType() {
    return retType;
  }
  
  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************
  
  public boolean match(MatchNode matchNode, MatchEngine engine) {

    if(!super.match(matchNode, engine)) {
      return false;
    }
    
    Integer type = (Integer)matchNode.getRuleValue(MatchProperties.EXPRENT_EXITTYPE);
    if(type != null) {
      if(this.exitType != type.intValue()) {
        return false;
      }
    }
    
    return true;
  }
  
}
