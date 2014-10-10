/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.groovy;

import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.batch.Main;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class GreclipseMain extends Main {
  private final Map<String, List<String>> myOutputs;

  public GreclipseMain(PrintWriter outWriter, PrintWriter errWriter, Map customDefaultOptions, Map<String, List<String>> outputs) {
    super(new PrintWriter(outWriter), new PrintWriter(errWriter), false, customDefaultOptions, null);
    myOutputs = outputs;
  }

  @Override
  public void outputClassFiles(CompilationResult result) {
    super.outputClassFiles(result);

    if (result == null || result.hasErrors() && !proceedOnError) {
      return;
    }

    List<String> classFiles = new ArrayList<String>();
    for (ClassFile file : result.getClassFiles()) {
      classFiles.add(new String(file.fileName()) + ".class");
    }
    myOutputs.put(new String(result.getFileName()), classFiles);
  }

}
