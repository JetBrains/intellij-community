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

import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;
import com.intellij.rt.execution.testFrameworks.ChildVMStarter;

import java.io.PrintStream;
import java.util.ArrayList;

public class JUnitForkedStarter extends ChildVMStarter {

  public static void main(String[] args) throws Exception {
    new JUnitForkedStarter().startVM(args);
  }

  protected void configureFrameworkAndRun(String[] args, PrintStream out, PrintStream err)
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

}
