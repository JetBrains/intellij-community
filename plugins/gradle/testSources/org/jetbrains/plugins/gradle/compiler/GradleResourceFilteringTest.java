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
package org.jetbrains.plugins.gradle.compiler;

import org.junit.Test;

/**
 * @author Vladislav.Soroka
 * @since 7/21/2014
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleResourceFilteringTest extends GradleCompilingTestCase {

  @Test
  public void testHeadFilter() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", "1 Header\n" +
                                         "2\n" +
                                         "3 another text\n" +
                                         "4\n" +
                                         "5 another text \n" +
                                         "6 another text @token@ another text\n" +
                                         "7\n" +
                                         "8 Footer");
    importProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "import org.apache.tools.ant.filters.*\n" +
      "processResources {\n" +
      "  filter(HeadFilter, lines:3, skip:2)\n" +
      "}"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main");

    assertCopied("build/resources/main/dir/file.txt", "3 another text\n" +
                                                      "4\n" +
                                                      "5 another text \n");
  }

  @Test
  public void testReplaceTokensFilter() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", "1 Header\n" +
                                         "2\n" +
                                         "3 #token1#another text\n" +
                                         "4\n" +
                                         "5 another text \n" +
                                         "6 another text #token2# another text\n" +
                                         "7\n" +
                                         "8 Footer");
    importProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "import org.apache.tools.ant.filters.*\n" +
      "processResources {\n" +
      "  filter(ReplaceTokens, tokens:[token1:'<11111>', token2:'<2222>'], beginToken: '#', endToken: '#')\n" +
      "}"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main");

    assertCopied("build/resources/main/dir/file.txt", "1 Header\n" +
                                                      "2\n" +
                                                      "3 <11111>another text\n" +
                                                      "4\n" +
                                                      "5 another text \n" +
                                                      "6 another text <2222> another text\n" +
                                                      "7\n" +
                                                      "8 Footer");
  }

  @Test
  public void testRenameFilter() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.txt");
    importProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "import org.apache.tools.ant.filters.*\n" +
      "processResources {\n" +
      "  rename 'file.txt', 'file001.txt'\n" +
      "}"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main");

    assertCopied("build/resources/main/dir/file001.txt");
  }

  @Test
  public void testExpandPropertiesFilter() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", "some text ${myProp} another text");
    importProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "import org.apache.tools.ant.filters.*\n" +
      "ant.project.setProperty('myProp', 'myPropValue')\n" +
      "processResources {\n" +
      "  filter (ExpandProperties, project: ant.project)\n" +
      "}"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main");

    assertCopied("build/resources/main/dir/file.txt", "some text myPropValue another text");
  }

  @Test
  public void testFiltersChain() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", "1 Header\n" +
                                         "2\n" +
                                         "3 another text@token1@\n" +
                                         "4\n" +
                                         "5 another text \n" +
                                         "6 another text @token2@ another text\n" +
                                         "7\n" +
                                         "8 Footer");
    importProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "import org.apache.tools.ant.filters.*\n" +
      "processResources {\n" +
      "  filter(HeadFilter, lines:4, skip:2)\n" +
      "  filter(ReplaceTokens, tokens:[token1:'<11111>', token2:'<2222>'])\n" +
      "  rename 'file.txt', 'file001.txt'\n" +
      "}"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main");

    assertCopied("build/resources/main/dir/file001.txt", "3 another text<11111>\n" +
                                                         "4\n" +
                                                         "5 another text \n" +
                                                         "6 another text <2222> another text");
  }
}