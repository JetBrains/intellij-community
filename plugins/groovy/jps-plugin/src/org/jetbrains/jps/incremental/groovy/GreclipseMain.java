// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy;

import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.batch.Main;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GreclipseMain extends Main {
  private final Map<String, List<String>> myOutputs;

  public GreclipseMain(PrintWriter outWriter, PrintWriter errWriter, Map<String, List<String>> outputs) {
    super(new PrintWriter(outWriter), new PrintWriter(errWriter), false, null, null);
    myOutputs = outputs;
  }

  @Override
  public void outputClassFiles(CompilationResult result) {
    super.outputClassFiles(result);

    if (result == null || result.hasErrors() && !proceedOnError) {
      return;
    }

    List<String> classFiles = new ArrayList<>();
    for (ClassFile file : result.getClassFiles()) {
      classFiles.add(new String(file.fileName()) + ".class");
    }
    myOutputs.put(new String(result.getFileName()), classFiles);
  }

}
