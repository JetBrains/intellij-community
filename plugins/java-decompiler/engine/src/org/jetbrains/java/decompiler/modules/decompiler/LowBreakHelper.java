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
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.SynchronizedStatement;

import java.util.List;

public class LowBreakHelper {

  public static void lowBreakLabels(Statement root) {

    lowBreakLabelsRec(root);

    liftBreakLabels(root);
  }

  private static void lowBreakLabelsRec(Statement stat) {

    while (true) {

      boolean found = false;

      for (StatEdge edge : stat.getLabelEdges()) {
        if (edge.getType() == StatEdge.TYPE_BREAK) {
          Statement minclosure = getMinClosure(stat, edge.getSource());
          if (minclosure != stat) {
            minclosure.addLabeledEdge(edge);
            edge.labeled = isBreakEdgeLabeled(edge.getSource(), minclosure);
            found = true;
            break;
          }
        }
      }

      if (!found) {
        break;
      }
    }

    for (Statement st : stat.getStats()) {
      lowBreakLabelsRec(st);
    }
  }

  public static boolean isBreakEdgeLabeled(Statement source, Statement closure) {

    if (closure.type == Statement.TYPE_DO || closure.type == Statement.TYPE_SWITCH) {

      Statement parent = source.getParent();

      if (parent == closure) {
        return false;
      }
      else {
        return isBreakEdgeLabeled(parent, closure) ||
               (parent.type == Statement.TYPE_DO || parent.type == Statement.TYPE_SWITCH);
      }
    }
    else {
      return true;
    }
  }

  public static Statement getMinClosure(Statement closure, Statement source) {

    while (true) {

      Statement newclosure = null;

      switch (closure.type) {
        case Statement.TYPE_SEQUENCE:
          Statement last = closure.getStats().getLast();

          if (isOkClosure(closure, source, last)) {
            newclosure = last;
          }
          break;
        case Statement.TYPE_IF:
          IfStatement ifclosure = (IfStatement)closure;
          if (isOkClosure(closure, source, ifclosure.getIfstat())) {
            newclosure = ifclosure.getIfstat();
          }
          else if (isOkClosure(closure, source, ifclosure.getElsestat())) {
            newclosure = ifclosure.getElsestat();
          }
          break;
        case Statement.TYPE_TRYCATCH:
          for (Statement st : closure.getStats()) {
            if (isOkClosure(closure, source, st)) {
              newclosure = st;
              break;
            }
          }
          break;
        case Statement.TYPE_SYNCRONIZED:
          Statement body = ((SynchronizedStatement)closure).getBody();

          if (isOkClosure(closure, source, body)) {
            newclosure = body;
          }
      }

      if (newclosure == null) {
        break;
      }

      closure = newclosure;
    }

    return closure;
  }

  private static boolean isOkClosure(Statement closure, Statement source, Statement stat) {

    boolean ok = false;

    if (stat != null && stat.containsStatementStrict(source)) {

      List<StatEdge> lst = stat.getAllSuccessorEdges();

      ok = lst.isEmpty();
      if (!ok) {
        StatEdge edge = lst.get(0);
        ok = (edge.closure == closure && edge.getType() == StatEdge.TYPE_BREAK);
      }
    }

    return ok;
  }


  private static void liftBreakLabels(Statement stat) {

    for (Statement st : stat.getStats()) {
      liftBreakLabels(st);
    }


    while (true) {

      boolean found = false;

      for (StatEdge edge : stat.getLabelEdges()) {
        if (edge.explicit && edge.labeled && edge.getType() == StatEdge.TYPE_BREAK) {

          Statement newclosure = getMaxBreakLift(stat, edge);

          if (newclosure != null) {
            newclosure.addLabeledEdge(edge);
            edge.labeled = isBreakEdgeLabeled(edge.getSource(), newclosure);

            found = true;
            break;
          }
        }
      }

      if (!found) {
        break;
      }
    }
  }

  private static Statement getMaxBreakLift(Statement stat, StatEdge edge) {

    Statement closure = null;
    Statement newclosure = stat;

    while ((newclosure = getNextBreakLift(newclosure, edge)) != null) {
      closure = newclosure;
    }

    return closure;
  }

  private static Statement getNextBreakLift(Statement stat, StatEdge edge) {

    Statement closure = stat.getParent();

    while (closure != null && !closure.containsStatementStrict(edge.getDestination())) {

      boolean labeled = isBreakEdgeLabeled(edge.getSource(), closure);
      if (closure.isLabeled() || !labeled) {
        return closure;
      }

      closure = closure.getParent();
    }

    return null;
  }
}
