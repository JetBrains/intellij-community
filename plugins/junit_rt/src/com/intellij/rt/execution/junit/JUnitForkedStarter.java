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

import com.intellij.rt.execution.testFrameworks.ChildVMStarter;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class JUnitForkedStarter extends ChildVMStarter {

  public static void main(String[] args) throws Exception {
    List argList = new ArrayList();
    for (int i = 0; i < args.length; i++) {
      final int count = RepeatCount.getCount(args[i]);
      if (count > 0) {
        JUnitStarter.ourCount = count;
        continue;
      }
      argList.add(args[i]);
    }
    new JUnitForkedStarter().startVM((String[])argList.toArray(new String[argList.size()]));
  }

  protected void configureFrameworkAndRun(String[] args, PrintStream out, PrintStream err)
    throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    final String[] childTestDescription = {args[1]};
    final String argentName = args[2];
    final ArrayList listeners = new ArrayList();
    for (int i = 3, argsLength = args.length; i < argsLength; i++) {
      listeners.add(args[i]);
    }
    IdeaTestRunner testRunner = (IdeaTestRunner)JUnitStarter.getAgentClass(argentName).newInstance();
    System.exit(testRunner.startRunnerWithArgs(childTestDescription, listeners, null, JUnitStarter.ourCount, false));
  }

}
