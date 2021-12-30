package org.jetbrains.java.decompiler.modules.decompiler.typeann;

import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.struct.StructTypePath;

import java.util.Deque;

public class TypePathWriteProgress {
  private final Deque<StructTypePath> paths;

  private final TypeAnnotation annotation;

  public TypePathWriteProgress(TypeAnnotation annotation, Deque<StructTypePath> paths) {
    this.annotation = annotation;
    this.paths = paths;
  }

  public Deque<StructTypePath> getPaths() {
    return paths;
  }

  public TypeAnnotation getAnnotation() {
    return annotation;
  }

  public void writeTypeAnnotation(StringBuilder sb) {
    String text = annotation.getAnnotationExpr().toJava(0, BytecodeMappingTracer.DUMMY).toString();
    sb.append(text);
    sb.append(' ');
  }
}
