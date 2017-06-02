/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.tests;


import com.intellij.testGuiFramework.framework.GuiTestLocalRunner;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.junit.runner.RunWith;

@RunWith(GuiTestLocalRunner.class)
public class IdeaCETestSuite {

  public static Test suite() {
    TestSuite mySuite = new TestSuite(com.intellij.testGuiFramework.tests.test.KotlinProjectTest.class);
    return mySuite;
  }

}

