/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.actions;

import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.model.MavenId;

import java.io.File;

/**
 * @author Vladislav.Soroka
 * @since 10/30/13
 */
public class AddGradleDslDependencyActionTest extends LightPlatformCodeInsightFixtureTestCase {

  public AddGradleDslDependencyActionTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  public void testAddMavenDependencyInEmptyFile() throws Exception {
    AddGradleDslDependencyAction.TEST_THREAD_LOCAL.set(ContainerUtil.list(new MavenId("testGroupId", "testArtifactId", "1.0")));
    doTest("testAddMavenDependencyInEmptyFile.gradle");
  }

  public void testAddMavenDependencyIntoExistingBlock() throws Exception {
    AddGradleDslDependencyAction.TEST_THREAD_LOCAL.set(ContainerUtil.list(new MavenId("testGroupId", "testArtifactId", "1.0")));
    doTest("testAddMavenDependencyIntoExistingBlock.gradle");
  }

  private void doTest(String file) {
    CodeInsightTestUtil.doActionTest(new AddGradleDslDependencyAction(), file, myFixture);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/gradle/testData";
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + getBasePath();
  }
}
