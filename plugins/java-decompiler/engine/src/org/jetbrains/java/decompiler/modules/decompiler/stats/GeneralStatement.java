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

import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;

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

    HashSet<Statement> set = new HashSet<>(statements);
    set.remove(head);

    for (Statement st : set) {
      stats.addWithKey(st, st.id);
    }

    this.post = post;
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buf = new TextBuffer();

    if (isLabeled()) {
      buf.appendIndent(indent).append("label").append(this.id.toString()).append(":").appendLineSeparator();
    }

    buf.appendIndent(indent).append("abstract statement {").appendLineSeparator();
    for (Statement stat : stats) {
      buf.append(stat.toJava(indent + 1, tracer));
    }
    buf.appendIndent(indent).append("}");

    return buf;
  }
}
