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
public class JUnitForkedSplitter<T> extends ForkedSplitter<T> {

  private IdeaTestRunner<T> myTestRunner;

  public JUnitForkedSplitter(String workingDirsPath, String forkMode, List newArgs) {
    super(workingDirsPath, forkMode, newArgs);
  }


  @Override
  protected String getStarterName() {
    return JUnitForkedStarter.class.getName();
  }

  @Override
  protected T createRootDescription(String[] args, String configName)
    throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    myTestRunner = (IdeaTestRunner<T>)JUnitStarter.getAgentClass(myNewArgs.get(0)).newInstance();
    return myTestRunner.getTestToStart(args, configName);
  }

  @Override
  protected String getTestClassName(T child) {
    return myTestRunner.getTestClassName(child);
  }

  @Override
  protected List<String> createChildArgs(T child) {
    List<String> newArgs = new ArrayList<String>();
    newArgs.add(myTestRunner.getStartDescription(child));
    newArgs.addAll(myNewArgs);
    return newArgs;
  }

  @Override
  protected List<String> createPerModuleArgs(String packageName,
                                             String workingDir,
                                             List<String> classNames,
                                             T rootDescription,
                                             String filters) throws IOException {
    File tempFile = File.createTempFile("idea_junit", ".tmp");
    tempFile.deleteOnExit();
    JUnitStarter.printClassesList(classNames, packageName, "", filters, tempFile);
    final List<String> childArgs = new ArrayList<String>();
    childArgs.add("@" + tempFile.getAbsolutePath());
    childArgs.addAll(myNewArgs);
    return childArgs;
  }


  @Override
  protected List<T> getChildren(T child) {
    return myTestRunner.getChildTests(child);
  }
}
