// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.junit;

import com.intellij.rt.execution.testFrameworks.ForkedSplitter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author anna
 */
public class JUnitForkedSplitter extends ForkedSplitter {

  private IdeaTestRunner myTestRunner;

  public JUnitForkedSplitter(String workingDirsPath, String forkMode, List newArgs) {
    super(workingDirsPath, forkMode, newArgs);
  }


  protected String getStarterName() {
    return JUnitForkedStarter.class.getName();
  }

  protected Object createRootDescription(String[] args, String configName)
    throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    myTestRunner = (IdeaTestRunner)JUnitStarter.getAgentClass((String)myNewArgs.get(0)).newInstance();
    return myTestRunner.getTestToStart(args, configName);
  }

  protected String getTestClassName(Object child) {
    return myTestRunner.getTestClassName(child);
  }

  protected List createChildArgs(Object child) {
    List newArgs = new ArrayList();
    newArgs.add(myTestRunner.getStartDescription(child));
    newArgs.addAll(myNewArgs);
    return newArgs;
  }
  
  protected List createPerModuleArgs(String packageName,
                                     String workingDir,
                                     List classNames,
                                     Object rootDescription, 
                                     String filters) throws IOException {
    File tempFile = File.createTempFile("idea_junit", ".tmp");
    tempFile.deleteOnExit();
    JUnitStarter.printClassesList(classNames, packageName, "", filters, tempFile);
    final List childArgs = new ArrayList();
    childArgs.add("@" + tempFile.getAbsolutePath());
    childArgs.addAll(myNewArgs);
    return childArgs;
  }


  protected List getChildren(Object child) {
    return myTestRunner.getChildTests(child);
  }
}
