/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.groovy.compiler.rt;

import groovy.lang.GroovyResourceLoader;
import org.codehaus.griffon.cli.CommandLineConstants;
import org.codehaus.griffon.compiler.DefaultImportCompilerCustomizer;
import org.codehaus.griffon.compiler.GriffonCompilerContext;
import org.codehaus.groovy.control.CompilationUnit;

import java.io.File;

/**
 * @author aalmiray
 * @author peter
 */
public class GriffonInjector extends CompilationUnitPatcher {
  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  public void patchCompilationUnit(CompilationUnit compilationUnit, GroovyResourceLoader resourceLoader, File[] srcFiles) {
    File baseDir = guessBaseDir(srcFiles);
    if (baseDir == null) {
      return;
    }

    GriffonCompilerContext.basedir = baseDir.getPath();
    GriffonCompilerContext.projectName = "IntelliJIDEARulezzzzz";
    GriffonCompilerContext.setup();
    if (!GriffonCompilerContext.getConfigOption(CommandLineConstants.KEY_DISABLE_AUTO_IMPORTS)) {
      DefaultImportCompilerCustomizer customizer = new DefaultImportCompilerCustomizer();
      customizer.collectDefaultImportsPerArtifact();
      compilationUnit.addPhaseOperation(customizer, customizer.getPhase().getPhaseNumber());
    }
  }

  private static File guessBaseDir(File srcFile) {
    File each = srcFile;
    while (each != null) {
      if (new File(each, "griffon-app").exists()) {
        return each;
      }
      each = each.getParentFile();
    }
    return null;
  }

  private static File guessBaseDir(File[] srcFiles) {
    for (File file : srcFiles) {
      File home = guessBaseDir(file);
      if (home != null) {
        return home;
      }
    }
    return null;
  }
}
