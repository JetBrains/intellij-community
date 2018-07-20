// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.File;
import java.util.Map;

@SuppressWarnings("unused")
public class BaseDecompiler {
  private final Fernflower engine;

  public BaseDecompiler(IBytecodeProvider provider, IResultSaver saver, Map<String, Object> options, IFernflowerLogger logger) {
    engine = new Fernflower(provider, saver, options, logger);
  }

  public void addSource(File source) {
    engine.addSource(source);
  }

  public void addLibrary(File library) {
    engine.addLibrary(library);
  }

  /** @deprecated use {@link #addSource(File)} / {@link #addLibrary(File)} instead */
  @Deprecated
  public void addSpace(File file, boolean own) {
    if (own) {
      addSource(file);
    }
    else {
      addLibrary(file);
    }
  }

  public void decompileContext() {
    try {
      engine.decompileContext();
    }
    finally {
      engine.clearContext();
    }
  }
}