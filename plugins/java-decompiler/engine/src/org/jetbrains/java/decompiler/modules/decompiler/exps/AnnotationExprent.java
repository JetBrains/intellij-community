// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.List;
import java.util.Objects;

public class AnnotationExprent extends Exprent {
  public static final int ANNOTATION_NORMAL = 1;
  public static final int ANNOTATION_MARKER = 2;
  public static final int ANNOTATION_SINGLE_ELEMENT = 3;

  private final String className;
  private final List<String> parNames;
  private final List<? extends Exprent> parValues;

  public AnnotationExprent(String className, List<String> parNames, List<? extends Exprent> parValues) {
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
    buffer.append(DecompilerContext.getImportCollector().getNestedName(ExprProcessor.buildJavaClassName(className)));

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
  public int hashCode() {
    return Objects.hash(className, parNames, parValues);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof AnnotationExprent ann)) return false;

    return className.equals(ann.className) &&
           parNames.equals(ann.parNames) &&
           parValues.equals(ann.parValues);
  }
}