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

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.List;


public class AnnotationExprent extends Exprent {

  public static final int ANNOTATION_NORMAL = 1;
  public static final int ANNOTATION_MARKER = 2;
  public static final int ANNOTATION_SINGLE_ELEMENT = 3;


  private String classname;

  private List<String> parnames;

  private List<Exprent> parvalues;

  {
    this.type = EXPRENT_ANNOTATION;
  }

  public AnnotationExprent(String classname, List<String> parnames, List<Exprent> parvalues) {
    this.classname = classname;
    this.parnames = parnames;
    this.parvalues = parvalues;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buffer = new TextBuffer();

    buffer.appendIndent(indent);
    buffer.append("@");
    buffer.append(DecompilerContext.getImportCollector().getShortName(ExprProcessor.buildJavaClassName(classname)));

    if (!parnames.isEmpty()) {
      buffer.append("(");
      if (parnames.size() == 1 && "value".equals(parnames.get(0))) {
        buffer.append(parvalues.get(0).toJava(indent + 1, tracer));
      }
      else {
        for (int i = 0; i < parnames.size(); i++) {
          buffer.appendLineSeparator().appendIndent(indent + 1);
          buffer.append(parnames.get(i));
          buffer.append(" = ");
          buffer.append(parvalues.get(i).toJava(indent + 2, tracer));

          if (i < parnames.size() - 1) {
            buffer.append(",");
          }
        }
        buffer.appendLineSeparator().appendIndent(indent);
      }

      buffer.append(")");
    }

    return buffer;
  }

  public int getAnnotationType() {

    if (parnames.isEmpty()) {
      return ANNOTATION_MARKER;
    }
    else {
      if (parnames.size() == 1 && "value".equals(parnames.get(0))) {
        return ANNOTATION_SINGLE_ELEMENT;
      }
      else {
        return ANNOTATION_NORMAL;
      }
    }
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof AnnotationExprent)) return false;

    AnnotationExprent ann = (AnnotationExprent)o;
    return classname.equals(ann.classname) &&
           InterpreterUtil.equalLists(parnames, ann.parnames) &&
           InterpreterUtil.equalLists(parvalues, ann.parvalues);
  }

  public String getClassname() {
    return classname;
  }
}
