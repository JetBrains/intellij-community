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
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.List;

public class AnnotationExprent extends Exprent {
  public static final int ANNOTATION_NORMAL = 1;
  public static final int ANNOTATION_MARKER = 2;
  public static final int ANNOTATION_SINGLE_ELEMENT = 3;

  private final String className;
  private final List<String> parNames;
  private final List<Exprent> parValues;

  public AnnotationExprent(String className, List<String> parNames, List<Exprent> parValues) {
    super(EXPRENT_ANNOTATION);
    this.className = className;
    this.parNames = parNames;
    this.parValues = parValues;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buffer = new TextBuffer();

    buffer.appendIndent(indent);
    buffer.append('@');
    buffer.append(DecompilerContext.getImportCollector().getShortName(ExprProcessor.buildJavaClassName(className)));

    int type = getAnnotationType();

    if (type != ANNOTATION_MARKER) {
      buffer.append('(');

      boolean oneLiner = type == ANNOTATION_SINGLE_ELEMENT || indent < 0;

      for (int i = 0; i < parNames.size(); i++) {
        if (!oneLiner) {
          buffer.appendLineSeparator().appendIndent(indent + 1);
        }

        if (type != ANNOTATION_SINGLE_ELEMENT) {
          buffer.append(parNames.get(i));
          buffer.append(" = ");
        }

        buffer.append(parValues.get(i).toJava(0, tracer));

        if (i < parNames.size() - 1) {
          buffer.append(',');
        }
      }

      if (!oneLiner) {
        buffer.appendLineSeparator().appendIndent(indent);
      }

      buffer.append(')');
    }

    return buffer;
  }

  public String getClassName() {
    return className;
  }

  public int getAnnotationType() {
    if (parNames.isEmpty()) {
      return ANNOTATION_MARKER;
    }
    else if (parNames.size() == 1 && "value".equals(parNames.get(0))) {
      return ANNOTATION_SINGLE_ELEMENT;
    }
    else {
      return ANNOTATION_NORMAL;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof AnnotationExprent)) return false;

    AnnotationExprent ann = (AnnotationExprent)o;
    return className.equals(ann.className) &&
           InterpreterUtil.equalLists(parNames, ann.parNames) &&
           InterpreterUtil.equalLists(parValues, ann.parValues);
  }
}