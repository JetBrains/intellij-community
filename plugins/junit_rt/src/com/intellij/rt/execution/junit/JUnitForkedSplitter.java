/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import com.intellij.rt.execution.testFrameworks.ForkedSplitter;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author anna
 * @since 6.04.2011
 */
public class JUnitForkedSplitter extends ForkedSplitter {

  private IdeaTestRunner myTestRunner;

  public JUnitForkedSplitter(String workingDirsPath, String forkMode, PrintStream out, PrintStream err, List newArgs) {
    super(workingDirsPath, forkMode, out, err, newArgs);
  }


  protected String getStarterName() {
    return JUnitForkedStarter.class.getName();
  }

  protected Object createRootDescription(String[] args, String configName)
    throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    myTestRunner = (IdeaTestRunner)JUnitStarter.getAgentClass(Boolean.valueOf((String)myNewArgs.get(0)).booleanValue()).newInstance();
    myTestRunner.setStreams(myOut, myErr, 0);
    return myTestRunner.getTestToStart(args, configName);
  }

  protected void sendTree(Object rootDescription) {
    TreeSender.sendTree(myTestRunner, rootDescription, !JUnitStarter.SM_RUNNER);
  }

  protected void sendTime(long time) {
    if (!JUnitStarter.SM_RUNNER) {
      new TimeSender(myTestRunner.getRegistry()).printHeader(System.currentTimeMillis() - time);
    }
  }

  protected Object findByClassName(String className, Object rootDescription) {
    final List children = getChildren(rootDescription);
    for (int i = 0; i < children.size(); i++) {
      Object child = children.get(i);
      if (className.equals(getTestClassName(child))) {
        return child;
      }
    }
    for (int i = 0; i < children.size(); i++) {
      final Object byName = findByClassName( className, children.get(i));
      if (byName != null) return byName;
    }
    return null;
  }

  protected String getTestClassName(Object child) {
    return myTestRunner.getTestClassName(child);
  }

  protected List createChildArgs(Object child) {
    List newArgs = new ArrayList();
    final OutputObjectRegistry registry = myTestRunner.getRegistry();
    newArgs.add(String.valueOf(registry != null ? registry.getKnownObject(child) : -1));
    newArgs.add(myTestRunner.getStartDescription(child));
    newArgs.addAll(myNewArgs);
    return newArgs;
  }
  
  protected List createPerModuleArgs(String packageName,
                                     String workingDir,
                                     List classNames,
                                     Object rootDescription) throws IOException {
    File tempFile = File.createTempFile("idea_junit", ".tmp");
    tempFile.deleteOnExit();
    JUnitStarter.printClassesList(classNames, packageName + ", working directory: \'" + workingDir + "\'", "", tempFile);
    final OutputObjectRegistry registry = myTestRunner.getRegistry();
    final String startIndex;
    if (registry != null) {
      startIndex = String.valueOf(registry.getKnownObject(findByClassName((String)classNames.get(0), rootDescription)));
    }
    else {
      startIndex = "-1";
    }
    final List childArgs = new ArrayList();
    childArgs.add(startIndex);
    childArgs.add("@" + tempFile.getAbsolutePath());
    childArgs.addAll(myNewArgs);
    return childArgs;
  }


  protected List getChildren(Object child) {
    return myTestRunner.getChildTests(child);
  }
}
