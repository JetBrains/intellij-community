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

package com.intellij.junit4;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.internal.runners.SuiteMethod;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runners.Parameterized;
import org.junit.runners.ParentRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.lang.reflect.Method;
import java.util.*;

class IdeaSuite extends Suite {
  private final String myName;

  IdeaSuite(List<Runner> runners, String name) throws InitializationError {
    super(null, runners);
    myName = name;
  }

  IdeaSuite(final RunnerBuilder builder, Class<?>[] classes, String name) throws InitializationError {
    super(builder, classes);
    myName = name;
  }

  @Override
  public Description getDescription() {
    Description description = Description.createSuiteDescription(myName, getTestClass().getAnnotations());
    try {
      final Method getFilteredChildrenMethod = ParentRunner.class.getDeclaredMethod("getFilteredChildren");
      getFilteredChildrenMethod.setAccessible(true);
      Collection<?> filteredChildren = (Collection<?>)getFilteredChildrenMethod.invoke(this);
      for (Object child : filteredChildren) {
        description.addChild(describeChild((Runner)child));
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return description;
  }

  @Override
  protected Description describeChild(Runner child) {
    final Description superDescription = super.describeChild(child);
    if (child instanceof ClassAwareSuiteMethod) {
      final Description description = Description.createSuiteDescription(((ClassAwareSuiteMethod)child).getKlass());
      ArrayList<Description> children = superDescription.getChildren();
      for (Description desc : children) {
        description.addChild(desc);
      }
      return description;
    }
    return superDescription;
  }

  @Override
  protected List<Runner> getChildren() {
    final List<Runner> children = new ArrayList<Runner>(super.getChildren());
    boolean containsSuiteInside = false;
    for (Runner child : children) {
      if (isSuite(child)) {
        containsSuiteInside = true;
        break;
      }
    }
    if (!containsSuiteInside) return children;
    try {
      final Set<String> allNames = new HashSet<String>();
      for (Runner child : children) {
        allNames.add(describeChild(child).getDisplayName());
      }
      for (Runner child : children) {
        if (isSuite(child)) {
          skipSuiteComponents(allNames, child);
        }
      }

      for (Iterator<Runner> iterator = children.iterator(); iterator.hasNext(); ) {
        Runner child = iterator.next();
        if (!isSuite(child) && !allNames.contains(describeChild(child).getDisplayName())) {
          iterator.remove();
        }
      }
    }
    catch (Throwable ignored){ }
    return children;
  }

  private static boolean isSuite(Object child) {
    return child instanceof Suite && !(child instanceof Parameterized) || child instanceof SuiteMethod;
  }

  private void skipSuiteComponents(Set<String> allNames, Object child) {
    try {
      if (child instanceof Suite) {
        final Method getChildrenMethod = Suite.class.getDeclaredMethod("getChildren");
        getChildrenMethod.setAccessible(true);
        final List<?> tests = (List<?>)getChildrenMethod.invoke(child);
        for (Object test : tests) {
          final String displayName = describeChild((Runner)test).getDisplayName();
          allNames.remove(displayName);
        }
      } else if (child instanceof SuiteMethod) {
        final Method getChildrenMethod = JUnit38ClassRunner.class.getDeclaredMethod("getTest");
        getChildrenMethod.setAccessible(true);
        final Test test = (Test)getChildrenMethod.invoke(child);
        if (test instanceof TestSuite) {
          final Enumeration<Test> tests = ((TestSuite)test).tests();
          while (tests.hasMoreElements()) {
            final Test t = tests.nextElement();
            if (t instanceof TestSuite) {
              final String testDescription = ((TestSuite)t).getName();
              allNames.remove(testDescription);
            }
          }
        }
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}