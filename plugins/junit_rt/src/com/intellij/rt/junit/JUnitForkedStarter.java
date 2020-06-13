// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.junit;

import com.intellij.rt.execution.junit.RepeatCount;

import java.util.ArrayList;
import java.util.List;

public class JUnitForkedStarter {

  public static void main(String[] args) throws Exception {
    List<String> argList = new ArrayList<String>();
    for (String arg : args) {
      final int count = RepeatCount.getCount(arg);
      if (count != 0) {
        JUnitStarter.ourCount = count;
        continue;
      }
      argList.add(arg);
    }
    args = argList.toArray(new String[0]);
    final String[] childTestDescription = {args[0]};
    final String argentName = args[1];
    final ArrayList<String> listeners = new ArrayList<String>();
    for (int i = 2, argsLength = args.length; i < argsLength; i++) {
      listeners.add(args[i]);
    }
    IdeaTestRunner<?> testRunner = (IdeaTestRunner<?>)JUnitStarter.getAgentClass(argentName).newInstance();
    System.exit(IdeaTestRunner.Repeater.startRunnerWithArgs(testRunner, childTestDescription, listeners, null, JUnitStarter.ourCount, false));
  }
}
