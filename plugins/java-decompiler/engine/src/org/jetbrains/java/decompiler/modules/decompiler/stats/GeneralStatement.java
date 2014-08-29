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
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.Collection;
import java.util.HashSet;


public class GeneralStatement extends Statement {

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  private GeneralStatement() {
    type = Statement.TYPE_GENERAL;
  }

  public GeneralStatement(Statement head, Collection<Statement> statements, Statement post) {

    this();

    first = head;
    stats.addWithKey(head, head.id);

    HashSet<Statement> set = new HashSet<Statement>(statements);
    set.remove(head);

    for (Statement st : set) {
      stats.addWithKey(st, st.id);
    }

    this.post = post;
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public String toJava(int indent) {
    String indstr = InterpreterUtil.getIndentString(indent);
    StringBuilder buf = new StringBuilder();

    String new_line_separator = DecompilerContext.getNewLineSeparator();

    if (isLabeled()) {
      buf.append(indstr).append("label").append(this.id).append(":").append(new_line_separator);
    }

    buf.append(indstr).append("abstract statement {").append(new_line_separator);
    for (int i = 0; i < stats.size(); i++) {
      buf.append(stats.get(i).toJava(indent + 1));
    }
    buf.append(indstr).append("}");

    return buf.toString();
  }
}
