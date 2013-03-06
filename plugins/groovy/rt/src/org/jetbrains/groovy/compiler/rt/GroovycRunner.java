/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import java.io.File;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 * @noinspection UseOfSystemOutOrSystemErr,CallToPrintStackTrace
 */

public class GroovycRunner {

  private GroovycRunner() {
  }

  /*
  private static Controller initController() {
    if (!"true".equals(System.getProperty("profile.groovy.compiler"))) {
      return null;
    }

    try {
      return new Controller();
    }
    catch (Exception ex) {
      ex.printStackTrace();
      return null;
    }
  }
  */


  public static void main(String[] args) {
    /*
    if (ourController != null) {
      try {
        ourController.startCPUProfiling(ProfilingModes.CPU_SAMPLING, null);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    */

    if (args.length != 2) {
      if (args.length != 3 || !"--indy".equals(args[2])) {
        System.err.println("There is no arguments for groovy compiler");
        System.exit(1);
      }
      System.setProperty("groovy.target.indy", "true");
    }

    final boolean forStubs = "stubs".equals(args[0]);
    final File argsFile = new File(args[1]);

    if (!argsFile.exists()) {
      System.err.println("Arguments file for groovy compiler not found");
      System.exit(1);
    }

    try {
      Class.forName("org.codehaus.groovy.control.CompilationUnit");
    }
    catch (Throwable e) {
      System.err.println(GroovyRtConstants.NO_GROOVY);
      System.exit(1);
    }

    try {
      DependentGroovycRunner.runGroovyc(forStubs, argsFile);
    }
    catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }
    /*
    finally {
      if (ourController != null) {
        try {
          ourController.captureSnapshot(ProfilingModes.SNAPSHOT_WITHOUT_HEAP);
          ourController.stopCPUProfiling();
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    */
  }
}
