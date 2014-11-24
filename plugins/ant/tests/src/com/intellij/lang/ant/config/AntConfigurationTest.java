/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntNoFileException;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.LightIdeaTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Created by andrey.myatlyuk on 11/23/14.
 */
public class AntConfigurationTest extends LightCodeInsightTestCase {

  private AntConfiguration configuration;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    configuration = AntConfiguration.getInstance(getProject());
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/config/";
  }

  public void testValidBuildFile() throws Exception {
    VirtualFile buildFile = getVirtualFile(getTestName(false) + ".xml");

    configuration.addBuildFile(buildFile);

    assertEquals(1, configuration.getBuildFiles().length);
  }

  public void testBuildFileWithoutProjectTag() throws Exception {
    VirtualFile buildFile = getVirtualFile(getTestName(false) + ".xml");

    try {
      configuration.addBuildFile(buildFile);
      fail("addBuildFile should throw an exception when build file does not have project tag");
    }
    catch (AntNoFileException ignored) {}
  }

  public void testBuildFileWithoutProjectName() throws Exception {
    VirtualFile buildFile = getVirtualFile(getTestName(false) + ".xml");

    try {
      configuration.addBuildFile(buildFile);
      fail("addBuildFile should throw an exception when build file does not have project name");
    }
    catch (AntNoFileException ignored) {}
  }

  public void testBuildFileWithoutDefaultTarget() throws Exception {
    VirtualFile buildFile = getVirtualFile(getTestName(false) + ".xml");

    try {
      configuration.addBuildFile(buildFile);
      fail("addBuildFile should throw an exception when build file does not have default target");
    }
    catch (AntNoFileException ignored) {}
  }

}
