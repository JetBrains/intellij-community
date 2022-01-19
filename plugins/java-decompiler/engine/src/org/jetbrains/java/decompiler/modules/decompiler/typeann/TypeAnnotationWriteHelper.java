// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.typeann;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.struct.StructTypePathEntry;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Wrapper around {@link TypeAnnotation} to maintain the state of the {@link StructTypePathEntry} list while writing.
 */
public class TypeAnnotationWriteHelper {
  private final @NotNull Deque<StructTypePathEntry> paths;

  private final @NotNull TypeAnnotation annotation;

  public TypeAnnotationWriteHelper(@NotNull TypeAnnotation annotation) {
    this(annotation, new ArrayDeque<>(annotation.getPaths()));
  }

  public TypeAnnotationWriteHelper(@NotNull TypeAnnotation annotation, @NotNull Deque<StructTypePathEntry> paths) {
    this.annotation = annotation;
    this.paths = paths;
  }

  /**
   * @return Active path relative to the current scope when writing.
   */
  public @NotNull Deque<StructTypePathEntry> getPaths() {
    return paths;
  }

  /**
   * @return The annotation to write
   */
  public @NotNull TypeAnnotation getAnnotation() {
    return annotation;
  }

  public void writeTo(@NotNull StringBuilder sb) {
    sb.append(this);
    sb.append(' ');
  }

  public void writeTo(@NotNull TextBuffer sb) {
    sb.append(toString());
    sb.append(' ');
  }

  @Override
  public String toString() {
    return annotation.getAnnotationExpr().toJava(0, BytecodeMappingTracer.DUMMY).toString();
  }
}
