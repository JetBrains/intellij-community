// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.groovy.compiler.rt;

import groovy.lang.GroovyResourceLoader;
import org.codehaus.groovy.control.CompilationUnit;

import java.io.File;

public abstract class CompilationUnitPatcher {

  public abstract void patchCompilationUnit(CompilationUnit compilationUnit, GroovyResourceLoader resourceLoader, File[] srcFiles);

}
