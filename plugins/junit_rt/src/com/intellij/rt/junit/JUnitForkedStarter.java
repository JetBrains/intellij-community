// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.junit;

import com.intellij.rt.execution.junit.RepeatCount;

import java.util.ArrayList;
import java.util.List;

public class JUnitForkedStarter {

  public static void main(String[] args) throws Exception {
    List argList = new ArrayList();
    for (int i = 0; i < args.length; i++) {
      final int count = RepeatCount.getCount(args[i]);
      if (count != 0) {
        JUnitStarter.ourCount = count;
        continue;
      }
      argList.add(args[i]);
    }
    args = (String[])argList.toArray(new String[0]);
    final String[] childTestDescription = {args[0]};
    final String argentName = args[1];
    final ArrayList listeners = new ArrayList();
    for (int i = 2, argsLength = args.length; i < argsLength; i++) {
      listeners.add(args[i]);
    }
    IdeaTestRunner testRunner = (IdeaTestRunner)JUnitStarter.getAgentClass(argentName).newInstance();
    System.exit(IdeaTestRunner.Repeater.startRunnerWithArgs(testRunner, childTestDescription, listeners, null, JUnitStarter.ourCount, false));
  }
}
