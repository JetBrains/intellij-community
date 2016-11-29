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
package com.intellij.testGuiFramework.impl;

import com.intellij.testGuiFramework.framework.GuiTestBase;
import com.intellij.util.net.HttpConfigurable;
import org.fest.swing.core.FastRobot;

import java.lang.reflect.InvocationTargetException;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.setUpDefaultProjectCreationLocationPath;

/**
 * @author Sergey Karashevich
 */
public class GuiTestCase extends GuiTestBase {

  public GuiTestCase() {
    super();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    setUpDefaultProjectCreationLocationPath();
    myRobot = new FastRobot();

    setIdeSettings();
    GitSettings.INSTANCE.setup();
  }

  @Override
  public void tearDown() throws InvocationTargetException, InterruptedException {
    GitSettings.INSTANCE.restore();
    super.tearDown();
  }

  private static void setIdeSettings() {
    // Clear HTTP proxy settings, in case a test changed them.
    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = false;
    ideSettings.PROXY_HOST = "";
    ideSettings.PROXY_PORT = 80;
  }


}
