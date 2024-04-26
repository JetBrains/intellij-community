// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.jps.model.java.compiler.CompilerOptions;

public final class GreclipseSettings implements CompilerOptions {
  public static final String COMPONENT_NAME = "GreclipseSettings";
  public static final String COMPONENT_FILE = "compiler.xml";

  public String greclipsePath = "";
  public boolean debugInfo = true;
  public String cmdLineParams = "";
  public String vmOptions = "";
}
