// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.jps.model.java.compiler.CompilerOptions;

/**
 * @author peter
 */
public final class GreclipseSettings implements CompilerOptions {
  public static final String COMPONENT_NAME = "GreclipseSettings";
  public static final String COMPONENT_FILE = "compiler.xml";

  public String greclipsePath = "";
  public boolean debugInfo = true;
  public String cmdLineParams = "";
}
