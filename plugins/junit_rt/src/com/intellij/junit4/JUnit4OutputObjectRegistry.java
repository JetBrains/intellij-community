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

/*
 * User: anna
 * Date: 05-Jun-2009
 */
package com.intellij.junit4;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import org.junit.runner.Description;

import java.util.ArrayList;


public class JUnit4OutputObjectRegistry extends OutputObjectRegistry {

  public JUnit4OutputObjectRegistry(PacketProcessor mainTransport, int lastIndex) {
    super(mainTransport, lastIndex);
  }

  protected int getTestCont(Object test) {
    return ((Description)test).testCount();
  }

  protected void addStringRepresentation(Object obj, Packet packet) {
    Description test = (Description)obj;
    if (test.isTest()) { //ignored test case has no children thought it hasn't method name
      final String methodName = JUnit4ReflectionUtil.getMethodName(test);
      final String className = JUnit4ReflectionUtil.getClassName(test);
      if (methodName != null && methodName.length() > 0) {
        addTestMethod(packet, methodName, className);
      } else {
        addTestClass(packet, className);
      }
    }
    else if (test.isSuite()) {
      String fullName = JUnit4ReflectionUtil.getClassName(test);
      if (fullName == null) {
        addUnknownTest(packet, test);
        return;
      }
      addTestClass(packet, fullName);
    }
    else {
      addUnknownTest(packet, test);
    }
  }

  protected Object createObjectWrapper(Object object) {
    return new ObjectWrapper(object);
  }

  private static class ObjectWrapper {
    private Object myObject;

    private ObjectWrapper(Object object) {
      myObject = object;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ObjectWrapper that = (ObjectWrapper)o;
      if (!myObject.equals(that.myObject)) return false;
      if (myObject instanceof Description && that.myObject instanceof Description) {
        final ArrayList children = ((Description)myObject).getChildren();
        final ArrayList thatChildren = ((Description)that.myObject).getChildren();
        if (children.size() != thatChildren.size()) return false;
        for (int i = 0, childrenSize = children.size(); i < childrenSize; i++) {
          if (!children.get(i).equals(thatChildren.get(i))) return false;
        }
      }

      return true;
    }

    public int hashCode() {
      int hash = myObject.hashCode();
      if (myObject instanceof Description) {
        final ArrayList children = ((Description)myObject).getChildren();
        for (int i = 0, childrenSize = children.size(); i < childrenSize; i++) {
          hash = 31 * hash + children.get(i).hashCode();
        }
      }
      return hash;
    }
  }
}
