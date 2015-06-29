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
import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author anna
 * @since 6.04.2011
 */
public class JUnitForkedStarter extends ForkedStarter {

  private IdeaTestRunner myTestRunner;

  JUnitForkedStarter() {
  }

  public static void main(String[] args) throws Exception {
    new JUnitForkedStarter().startVM(args);
  }

  protected String getStarterName() {
    return JUnitForkedStarter.class.getName();
  }

  protected void startVM(String[] args, PrintStream out, PrintStream err)
    throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    final int lastIdx = Integer.parseInt(args[1]);
    final String[] childTestDescription = {args[2]};
    final boolean isJUnit4 = args[3].equalsIgnoreCase("true");
    final ArrayList listeners = new ArrayList();
    for (int i = 4, argsLength = args.length; i < argsLength; i++) {
      listeners.add(args[i]);
    }
    IdeaTestRunner testRunner = (IdeaTestRunner)JUnitStarter.getAgentClass(isJUnit4).newInstance();
    //noinspection IOResourceOpenedButNotSafelyClosed
    testRunner.setStreams(new SegmentedOutputStream(out, true), new SegmentedOutputStream(err, true), lastIdx);
    System.exit(testRunner.startRunnerWithArgs(childTestDescription, listeners, null, 1, false));
  }

  int startForkedVMs(String workingDirsPath,
                     String[] args,
                     boolean isJUnit4,
                     List listeners,
                     String configName,
                     Object out,
                     Object err,
                     String forkMode,
                     String commandLinePath) throws Exception {
    List newArgs = new ArrayList();
    newArgs.add(String.valueOf(isJUnit4));
    newArgs.addAll(listeners);
    return startForkedVM(workingDirsPath, args, configName, out, err, forkMode, commandLinePath, newArgs);
  }

  protected Object createRootDescription(String[] args, List newArgs, String configName, Object out, Object err)
    throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    myTestRunner = (IdeaTestRunner)JUnitStarter.getAgentClass(Boolean.valueOf((String)newArgs.get(0)).booleanValue()).newInstance();
    myTestRunner.setStreams(out, err, 0);
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

  protected List createChildArgs(List args, Object child) {
    List newArgs = new ArrayList();
    final OutputObjectRegistry registry = myTestRunner.getRegistry();
    newArgs.add(String.valueOf(registry != null ? registry.getKnownObject(child) : -1));
    newArgs.add(myTestRunner.getStartDescription(child));
    newArgs.addAll(args);
    return newArgs;
  }
  
  protected List createChildArgsForClasses(List newArgs, String packageName, String workingDir, List classNames, Object rootDescriptor)
    throws IOException {
    File tempFile = File.createTempFile("idea_junit", ".tmp");
    tempFile.deleteOnExit();
    JUnitStarter.printClassesList(classNames, packageName + ", working directory: \'" + workingDir + "\'", "", tempFile);
    final OutputObjectRegistry registry = myTestRunner.getRegistry();
    final String startIndex = String.valueOf(registry != null ? registry.getKnownObject(rootDescriptor) : -1);
    final List childArgs = new ArrayList();
    childArgs.add(startIndex);
    childArgs.add("@" + tempFile.getAbsolutePath());
    childArgs.addAll(newArgs);
    return childArgs;
  }


  protected List getChildren(Object child) {
    return myTestRunner.getChildTests(child);
  }

  protected PrintStream wrapOutputStream(OutputStream out) {
    return JUnitStarter.SM_RUNNER ? ((PrintStream)out) : ((SegmentedOutputStream)out).getPrintStream();
  }
}
