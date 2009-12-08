/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class VcsInitialization {
  private final Project myProject;
  private final List<Pair<VcsInitObject, Runnable>> myList;

  public VcsInitialization(Project project) {
    myProject = project;
    myList = new LinkedList<Pair<VcsInitObject, Runnable>>();

    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        execute();
      }
    });
  }

  public void add(final VcsInitObject vcsInitObject, final Runnable runnable) {
    myList.add(new Pair<VcsInitObject, Runnable>(vcsInitObject, runnable));
  }

  public void execute() {
    Collections.sort(myList, new Comparator<Pair<VcsInitObject, Runnable>>() {
      public int compare(Pair<VcsInitObject, Runnable> o1, Pair<VcsInitObject, Runnable> o2) {
        return new Integer(o1.getFirst().getOrder()).compareTo(new Integer(o2.getFirst().getOrder()));
      }
    });
    for (Pair<VcsInitObject, Runnable> pair : myList) {
      pair.getSecond().run();
    }
  }
}
