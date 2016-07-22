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

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.IMatchable;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;

import java.util.*;
import java.util.Map.Entry;

public class Exprent implements IMatchable {
  public static final int MULTIPLE_USES = 1;
  public static final int SIDE_EFFECTS_FREE = 2;
  public static final int BOTH_FLAGS = 3;

  public static final int EXPRENT_ARRAY = 1;
  public static final int EXPRENT_ASSIGNMENT = 2;
  public static final int EXPRENT_CONST = 3;
  public static final int EXPRENT_EXIT = 4;
  public static final int EXPRENT_FIELD = 5;
  public static final int EXPRENT_FUNCTION = 6;
  public static final int EXPRENT_IF = 7;
  public static final int EXPRENT_INVOCATION = 8;
  public static final int EXPRENT_MONITOR = 9;
  public static final int EXPRENT_NEW = 10;
  public static final int EXPRENT_SWITCH = 11;
  public static final int EXPRENT_VAR = 12;
  public static final int EXPRENT_ANNOTATION = 13;
  public static final int EXPRENT_ASSERT = 14;

  public final int type;
  public final int id;
  public Set<Integer> bytecode = null;  // offsets of bytecode instructions decompiled to this exprent

  public Exprent(int type) {
    this.type = type;
    this.id = DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.EXPRENT_COUNTER);
  }

  public int getPrecedence() {
    return 0; // the highest precedence
  }

  public VarType getExprType() {
    return VarType.VARTYPE_VOID;
  }

  public int getExprentUse() {
    return 0;
  }

  public CheckTypesResult checkExprTypeBounds() {
    return new CheckTypesResult();
  }

  public boolean containsExprent(Exprent exprent) {
    List<Exprent> listTemp = new ArrayList<>(getAllExprents(true));
    listTemp.add(this);

    for (Exprent lstExpr : listTemp) {
      if (lstExpr.equals(exprent)) {
        return true;
      }
    }

    return false;
  }

  public List<Exprent> getAllExprents(boolean recursive) {
    List<Exprent> lst = getAllExprents();
    if (recursive) {
      for (int i = lst.size() - 1; i >= 0; i--) {
        lst.addAll(lst.get(i).getAllExprents(true));
      }
    }
    return lst;
  }

  public Set<VarVersionPair> getAllVariables() {
    List<Exprent> lstAllExprents = getAllExprents(true);
    lstAllExprents.add(this);

    Set<VarVersionPair> set = new HashSet<>();
    for (Exprent expr : lstAllExprents) {
      if (expr.type == EXPRENT_VAR) {
        set.add(new VarVersionPair((VarExprent)expr));
      }
    }
    return set;
  }

  public List<Exprent> getAllExprents() {
    throw new RuntimeException("not implemented");
  }

  public Exprent copy() {
    throw new RuntimeException("not implemented");
  }

  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    throw new RuntimeException("not implemented");
  }

  public void replaceExprent(Exprent oldExpr, Exprent newExpr) { }

  public void addBytecodeOffsets(Collection<Integer> bytecodeOffsets) {
    if (bytecodeOffsets != null && !bytecodeOffsets.isEmpty()) {
      if (bytecode == null) {
        bytecode = new HashSet<>(bytecodeOffsets);
      }
      else {
        bytecode.addAll(bytecodeOffsets);
      }
    }
  }
  
  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************

  @Override
  public IMatchable findObject(MatchNode matchNode, int index) {
    if (matchNode.getType() != MatchNode.MATCHNODE_EXPRENT) {
      return null;
    }

    List<Exprent> lstAllExprents = getAllExprents();
    if (lstAllExprents == null || lstAllExprents.isEmpty()) {
      return null;
    }

    String position = (String)matchNode.getRuleValue(MatchProperties.EXPRENT_POSITION);
    if (position != null) {
      if (position.matches("-?\\d+")) {
        return lstAllExprents
          .get((lstAllExprents.size() + Integer.parseInt(position)) % lstAllExprents.size()); // care for negative positions
      }
    }
    else if (index < lstAllExprents.size()) { // use 'index' parameter
      return lstAllExprents.get(index);
    }

    return null;
  }

  @Override
  public boolean match(MatchNode matchNode, MatchEngine engine) {
    if (matchNode.getType() != MatchNode.MATCHNODE_EXPRENT) {
      return false;
    }

    for (Entry<MatchProperties, RuleValue> rule : matchNode.getRules().entrySet()) {
      MatchProperties key = rule.getKey();
      if (key == MatchProperties.EXPRENT_TYPE && this.type != ((Integer)rule.getValue().value).intValue()) {
        return false;
      }
      if (key == MatchProperties.EXPRENT_RET && !engine.checkAndSetVariableValue((String)rule.getValue().value, this)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public String toString() {
    return toJava(0, BytecodeMappingTracer.DUMMY).toString();
  }
}