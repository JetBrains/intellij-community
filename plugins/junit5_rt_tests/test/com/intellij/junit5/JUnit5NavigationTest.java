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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.engine.descriptor.MethodTestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.TestIdentifier;

@DisplayName("junit 5 navigation features: location strings, etc")
class JUnit5NavigationTest {

  @Test
  void methodNavigation() throws Exception {
    UniqueId uniqueId = UniqueId.parse("[class:JUnit5NavigationTest]/[method:methodNavigation]");
    MethodTestDescriptor methodTestDescriptor =
      new MethodTestDescriptor(uniqueId, JUnit5NavigationTest.class, JUnit5NavigationTest.class.getDeclaredMethod("methodNavigation"));
    TestIdentifier testIdentifier = TestIdentifier.from(methodTestDescriptor);
    Assertions.assertEquals(JUnit5NavigationTest.class.getName(), JUnit5TestExecutionListener.getClassName(testIdentifier));
    Assertions.assertEquals("methodNavigation", JUnit5TestExecutionListener.getMethodName(testIdentifier));
    //Assertions.assertEquals("methodNavigation", testIdentifier.getDisplayName()); todo methodNavigation()
  }
}
