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

  public IdeaSuite(List runners, String name) throws InitializationError {
    super(null, runners);
    myName = name;
  }

  public IdeaSuite(final RunnerBuilder builder, Class[] classes, String name) throws InitializationError {
    super(builder, classes);
    myName = name;
  }

  public Description getDescription() {
    Description description = Description.createSuiteDescription(myName, getTestClass().getAnnotations());
    try {
      final Method getFilteredChildrenMethod = ParentRunner.class.getDeclaredMethod("getFilteredChildren", new Class[0]);
      getFilteredChildrenMethod.setAccessible(true);
      Collection filteredChildren = (Collection)getFilteredChildrenMethod.invoke(this, new Object[0]);
      for (Iterator iterator = filteredChildren.iterator(); iterator.hasNext();) {
        Object child = iterator.next();
        description.addChild(describeChild((Runner)child));
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return description;
  }

  protected Description describeChild(Runner child) {
    final Description superDescription = super.describeChild(child);
    if (child instanceof ClassAwareSuiteMethod) {
      final Description description = Description.createSuiteDescription(((ClassAwareSuiteMethod)child).getKlass());
      ArrayList children = superDescription.getChildren();
      for (int i = 0, size = children.size(); i < size; i++) {
        description.addChild((Description)children.get(i));
      }
      return description;
    }
    return superDescription;
  }

  protected List getChildren() {
    final List children = new ArrayList(super.getChildren());
    boolean containsSuiteInside = false;
    for (Iterator iterator = children.iterator(); iterator.hasNext(); ) {
      Object child = iterator.next();
      if (isSuite(child)) {
        containsSuiteInside = true;
        break;
      }
    }
    if (!containsSuiteInside) return children;
    try {
      final Set allNames = new HashSet();
      for (Iterator iterator = children.iterator(); iterator.hasNext();) {
        final Object child = iterator.next();
        allNames.add(describeChild((Runner)child).getDisplayName());
      }
      for (Iterator iterator = children.iterator(); iterator.hasNext();) {
        final Object child = iterator.next();
        if (isSuite(child)) {
          skipSuiteComponents(allNames, child);
        }
      }

      for (Iterator iterator = children.iterator(); iterator.hasNext(); ) {
        Object child = iterator.next();
        if (!isSuite(child) && !allNames.contains(describeChild((Runner)child).getDisplayName())) {
          iterator.remove();
        }
      }
    }
    catch (Throwable e){ }
    return children;
  }

  private static boolean isSuite(Object child) {
    return child instanceof Suite && !(child instanceof Parameterized) || child instanceof SuiteMethod;
  }

  private void skipSuiteComponents(Set allNames, Object child) {
    try {
      if (child instanceof Suite) {
        final Method getChildrenMethod = Suite.class.getDeclaredMethod("getChildren", new Class[0]);
        getChildrenMethod.setAccessible(true);
        final List tests = (List)getChildrenMethod.invoke(child, new Object[0]);
        for (Iterator suiteIterator = tests.iterator(); suiteIterator.hasNext();) {
          final String displayName = describeChild((Runner)suiteIterator.next()).getDisplayName();
          if (allNames.contains(displayName)) {
            allNames.remove(displayName);
          }
        }
      } else if (child instanceof SuiteMethod) {
        final Method getChildrenMethod = JUnit38ClassRunner.class.getDeclaredMethod("getTest", new Class[0]);
        getChildrenMethod.setAccessible(true);
        final Test test = (Test)getChildrenMethod.invoke(child, new Object[0]);
        if (test instanceof TestSuite) {
          final Enumeration tests = ((TestSuite)test).tests();
          while (tests.hasMoreElements()) {
            final Test t = (Test)tests.nextElement();
            if (t instanceof TestSuite) {
              final String testDescription = ((TestSuite)t).getName();
              if (allNames.contains(testDescription)) {
                allNames.remove(testDescription);
              }
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