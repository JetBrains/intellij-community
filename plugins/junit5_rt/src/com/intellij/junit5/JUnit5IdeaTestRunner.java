/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.junit5;

import com.intellij.rt.execution.junit.IdeaTestRunner;
import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import org.junit.gen5.launcher.Launcher;
import org.junit.gen5.launcher.TestDiscoveryRequest;
import org.junit.gen5.launcher.TestPlan;
import org.junit.gen5.launcher.main.LauncherFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JUnit5IdeaTestRunner implements IdeaTestRunner {
  private JUnit5TestExecutionListener myListener;

  @Override
  public int startRunnerWithArgs(String[] args, ArrayList listeners, String name, int count, boolean sendTree) {
    Launcher launcher = LauncherFactory.create();
    launcher.registerTestExecutionListeners(myListener);
    final String[] packageNameRef = new String[1];
    final TestDiscoveryRequest discoveryRequest = JUnit5TestRunnerUtil.buildRequest(args, packageNameRef);
    final TestPlan testPlan = launcher.discover(discoveryRequest);
    myListener.sendTree(testPlan, packageNameRef[0]);
    launcher.execute(discoveryRequest);

    return 0;
  }

  @Override
  public void setStreams(Object segmentedOut, Object segmentedErr, int lastIdx) {
    myListener = new JUnit5TestExecutionListener(System.out);
  }

  @Override
  public OutputObjectRegistry getRegistry() {
    return null;
  }

  //forked mode todo not supported
  
  @Override
  public Object getTestToStart(String[] args, String name) {
    return null;
  }

  @Override
  public List getChildTests(Object description) {
    return Collections.emptyList();
  }

  @Override
  public String getStartDescription(Object child) {
    return null;
  }

  @Override
  public String getTestClassName(Object child) {
    return child.toString();
  }
}
