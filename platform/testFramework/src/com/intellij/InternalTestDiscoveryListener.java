// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Used in TestAll to collect data in command line
 */
@SuppressWarnings({"unused", "UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class InternalTestDiscoveryListener extends TestDiscoveryBasicListener implements Closeable {
  private final String myModuleName;
  private final String myTracesFile;
  private Object myDiscoveryIndex;
  private Class<?> myDiscoveryIndexClass;

  public InternalTestDiscoveryListener() {
    myTracesFile = System.getProperty("org.jetbrains.instrumentation.trace.file");
    if (myTracesFile == null) throw new IllegalArgumentException();
    myModuleName = System.getProperty("org.jetbrains.instrumentation.main.module");
    if (myModuleName == null) throw new IllegalArgumentException();
    System.out.println(getClass().getSimpleName() + " instantiated with module='" + myModuleName + "' , directory='" + myTracesFile + "'");
  }

  private Object getIndex() {
    if (myDiscoveryIndex == null) {
      Project project = ProjectManager.getInstance().getDefaultProject();
      try {
        myDiscoveryIndexClass = Class.forName("com.intellij.execution.testDiscovery.TestDiscoveryIndex");
        myDiscoveryIndex = myDiscoveryIndexClass
          .getConstructor(Project.class, String.class)
          .newInstance(project, myTracesFile);
      }
      catch (Throwable e) {
        e.printStackTrace();
      }
    }
    return myDiscoveryIndex;
  }

  @Override
  public void close() throws IOException {
    System.out.println("Start compacting to index");
    try {
      Object index = getIndex();
      Method method = Class.forName("com.intellij.execution.testDiscovery.TestDiscoveryExtension")
                           .getMethod("processTracesFile", String.class, String.class, String.class, myDiscoveryIndexClass);
      method.invoke(null, myTracesFile, myModuleName, "j", index);
      System.out.println("Compacting done.");
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
    zipOutput(myTracesFile);
  }

  private static void zipOutput(String traceFilePath) {
    File traceFile = new File(traceFilePath);
    File parent = traceFile.getParentFile();
    String zipName = traceFile.getName() + ".zip";
    System.out.println("Preparing zip.");
    try {
      File zipFile = new File(parent, zipName);
      ZipUtil.compressFile(traceFile, zipFile);
      FileUtil.delete(traceFile);
      System.out.println("archive " + zipFile.getPath() + " prepared");
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
