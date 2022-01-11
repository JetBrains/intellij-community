package org.jetbrains.java.decompiler.modules.decompiler.typeann;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.struct.StructTypePath;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Wrapper around {@link TypeAnnotation} to maintain the state of the {@link StructTypePath} list while writing.
 */
public class TypeAnnotationWriteHelper {
  private final @NotNull Deque<StructTypePath> paths;

  private final @NotNull TypeAnnotation annotation;

  public TypeAnnotationWriteHelper(@NotNull TypeAnnotation annotation) {
    this(annotation, new ArrayDeque<>(annotation.getPaths()));
  }

  public TypeAnnotationWriteHelper(@NotNull TypeAnnotation annotation, @NotNull Deque<StructTypePath> paths) {
    this.annotation = annotation;
    this.paths = paths;
  }

  /**
   * @return Active path relative to the current scope when writing.
   */
  public @NotNull Deque<StructTypePath> getPaths() {
    return paths;
  }

  /**
   * @return The annotation to write
   */
  public @NotNull TypeAnnotation getAnnotation() {
    return annotation;
  }

  public void writeTo(@NotNull StringBuilder sb) {
    String text = annotation.getAnnotationExpr().toJava(0, BytecodeMappingTracer.DUMMY).toString();
    sb.append(text);
    sb.append(' ');
  }

  public void writeTo(@NotNull TextBuffer sb) {
    String text = annotation.getAnnotationExpr().toJava(0, BytecodeMappingTracer.DUMMY).toString();
    sb.append(text);
    sb.append(' ');
  }
}
