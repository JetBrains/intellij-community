// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip.fixtures;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * An in-memory {@link JavaFileObject} for storing compiled class bytecode.
 * Used by {@link InMemoryFileManager} to capture compiler output without writing to disk.
 */
final class InMemoryClassFile extends SimpleJavaFileObject {
  private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

  InMemoryClassFile(String key) {
    super(URI.create("mem:///" + key + Kind.CLASS.extension), Kind.CLASS);
  }

  @Override
  public OutputStream openOutputStream() {
    return baos;
  }

  byte[] getBytes() {
    return baos.toByteArray();
  }
}
