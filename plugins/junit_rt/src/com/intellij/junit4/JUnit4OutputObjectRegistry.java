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


public class JUnit4OutputObjectRegistry extends OutputObjectRegistry {
  public JUnit4OutputObjectRegistry(PacketProcessor mainTransport, PacketProcessor auxilaryTransport) {
    super(mainTransport, auxilaryTransport);
  }

  protected int getTestCont(Object test) {
    return ((Description)test).testCount();
  }

  protected void addStringRepresentation(Object obj, Packet packet) {
    Description test = (Description)obj;
    if (test.isTest()) {
      addTestMethod(packet, JUnit4ReflectionUtil.getMethodName(test), JUnit4ReflectionUtil.getClassName(test));
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

}
