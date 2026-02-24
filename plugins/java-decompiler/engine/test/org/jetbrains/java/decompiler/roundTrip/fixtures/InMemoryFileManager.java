// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip.fixtures;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link JavaFileManager} that captures compiler output entirely in memory.
 * Class files are stored as {@link InMemoryClassFile} instances rather than
 * being written to disk. Call {@link #getClassBytes()} after compilation to
 * retrieve the compiled bytecode.
 */
public final class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
  private final Map<String, InMemoryClassFile> classFiles = new LinkedHashMap<>();

  public InMemoryFileManager(StandardJavaFileManager fileManager) {
    super(fileManager);
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
    if (kind == JavaFileObject.Kind.CLASS) {
      String key = className.replace('.', '/');
      InMemoryClassFile file = new InMemoryClassFile(key);
      classFiles.put(key, file);
      return file;
    }
    return super.getJavaFileForOutput(location, className, kind, sibling);
  }

  /**
   * Returns all compiled class bytes, keyed by slash-separated binary class name
   * (e.g. {@code "TestSimple"}, {@code "pkg/Foo$Bar"}).
   * Must be called after compilation is complete.
   */
  public Map<String, byte[]> getClassBytes() {
    Map<String, byte[]> result = new LinkedHashMap<>();
    for (Map.Entry<String, InMemoryClassFile> entry : classFiles.entrySet()) {
      result.put(entry.getKey(), entry.getValue().getBytes());
    }
    return result;
  }
}
