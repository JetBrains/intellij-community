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

import java.io.IOException;

/**
 * @author Vladislav.Soroka
 * @since 7/21/2014
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleResourceProcessingTest extends GradleCompilingTestCase {

  @Test
  public void testBasicResourceCopying() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    importProject(
      "apply plugin: 'java'"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main", "project_test");

    assertCopied("build/resources/main/dir/file.properties");
    assertCopied("build/resources/test/dir/file-test.properties");
  }

  @Test
  public void testBasicResourceCopying_MergedProject() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    importProjectUsingSingeModulePerGradleProject(
      "apply plugin: 'java'"
    );
    assertModules("project");
    compileModules("project");

    assertCopied("build/resources/main/dir/file.properties");
    assertCopied("build/resources/test/dir/file-test.properties");
  }

  @Test
  public void testResourceCopyingFromSourcesFolder() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    createProjectSubFile("src/main/java/file.txt");
    importProject(
      "apply plugin: 'java'\n" +
      "sourceSets {\n" +
      "  main {\n" +
      "    resources.srcDir file('src/main/java')\n" +
      "  }\n" +
      "}"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main", "project_test");

    assertCopied("build/resources/main/dir/file.properties");
    assertCopied("build/resources/test/dir/file-test.properties");
    assertCopied("build/resources/main/file.txt");
  }

  @Test
  public void testResourceCopyingFromSourcesFolder_MergedProject() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    createProjectSubFile("src/main/java/file.txt");
    importProjectUsingSingeModulePerGradleProject(
      "apply plugin: 'java'\n" +
      "sourceSets {\n" +
      "  main {\n" +
      "    resources.srcDir file('src/main/java')\n" +
      "  }\n" +
      "}"
    );
    assertModules("project");
    compileModules("project");

    assertCopied("build/resources/main/dir/file.properties");
    assertCopied("build/resources/test/dir/file-test.properties");
    assertCopied("build/resources/main/file.txt");
  }

  @Test
  public void testResourceProcessingWithIdeaPluginCustomization() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    importProject(
      "apply plugin: 'java'\n" +
      "apply plugin: 'idea'\n" +
      "idea {\n" +
      "  module {\n" +
      "    inheritOutputDirs = false\n" +
      "    outputDir = file('muchBetterOutputDir')\n" +
      "    testOutputDir = file('muchBetterTestOutputDir')\n" +
      "  }\n" +
      "}"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main", "project_test");

    assertCopied("muchBetterOutputDir/dir/file.properties");
    assertCopied("muchBetterTestOutputDir/dir/file-test.properties");
  }

  @Test
  public void testResourceProcessingWithIdeaPluginCustomization_Merged() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    importProjectUsingSingeModulePerGradleProject(
      "apply plugin: 'java'\n" +
      "apply plugin: 'idea'\n" +
      "idea {\n" +
      "  module {\n" +
      "    inheritOutputDirs = false\n" +
      "    outputDir = file('muchBetterOutputDir')\n" +
      "    testOutputDir = file('muchBetterTestOutputDir')\n" +
      "  }\n" +
      "}"
    );
    assertModules("project");
    compileModules("project");

    assertCopied("muchBetterOutputDir/dir/file.properties");
    assertCopied("muchBetterTestOutputDir/dir/file-test.properties");
  }

  @Test
  public void testIncludesAndExcludesInSourceSets() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "sourceSets {\n" +
      "  main {\n" +
      "    resources {\n" +
      "      include '**/*.yyy'\n" +
      "      include '**/*.xxx'\n" +
      "      exclude 'dir/*.yyy'\n" +
      "      exclude '*.xxx'\n" +
      "    }\n" +
      "  }\n" +
      "  test {\n" +
      "    resources {\n" +
      "      include '**/*.yyy'\n" +
      "      include '**/*.xxx'\n" +
      "      exclude 'dir/*.yyy'\n" +
      "      exclude '*.xxx'\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main", "project_test");

    assertCopiedResources();
  }

  @Test
  public void testIncludesAndExcludesInSourceSets_MergedProject() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProjectUsingSingeModulePerGradleProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "sourceSets {\n" +
      "  main {\n" +
      "    resources {\n" +
      "      include '**/*.yyy'\n" +
      "      include '**/*.xxx'\n" +
      "      exclude 'dir/*.yyy'\n" +
      "      exclude '*.xxx'\n" +
      "    }\n" +
      "  }\n" +
      "  test {\n" +
      "    resources {\n" +
      "      include '**/*.yyy'\n" +
      "      include '**/*.xxx'\n" +
      "      exclude 'dir/*.yyy'\n" +
      "      exclude '*.xxx'\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );
    assertModules("project");
    compileModules("project");

    assertCopiedResources();
  }

  @Test
  public void testIncludesAndExcludesInAllSourceSets() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "sourceSets.all {\n" +
      "  resources {\n" +
      "    include '**/*.yyy'\n" +
      "    include '**/*.xxx'\n" +
      "    exclude 'dir/*.yyy'\n" +
      "    exclude '*.xxx'\n" +
      "  }\n" +
      "}"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main", "project_test");

    assertCopiedResources();
  }

  @Test
  public void testIncludesAndExcludesInAllSourceSets_MergedProject() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProjectUsingSingeModulePerGradleProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "sourceSets.all {\n" +
      "  resources {\n" +
      "    include '**/*.yyy'\n" +
      "    include '**/*.xxx'\n" +
      "    exclude 'dir/*.yyy'\n" +
      "    exclude '*.xxx'\n" +
      "  }\n" +
      "}"
    );
    assertModules("project");
    compileModules("project");

    assertCopiedResources();
  }

  @Test
  public void testIncludesAndExcludesInResourcesTask() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "processResources {\n" +
      "  include '**/*.yyy'\n" +
      "  include '**/*.xxx'\n" +
      "  exclude 'dir/*.yyy'\n" +
      "  exclude '*.xxx'\n" +
      "}\n" +
      "\n" +
      "processTestResources {\n" +
      "  include '**/*.yyy'\n" +
      "  include '**/*.xxx'\n" +
      "  exclude 'dir/*.yyy'\n" +
      "  exclude '*.xxx'\n" +
      "}\n"
    );
    assertModules("project", "project_main", "project_test");
    compileModules("project_main", "project_test");

    assertCopiedResources();
  }

  @Test
  public void testIncludesAndExcludesInResourcesTask_MergedProject() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProjectUsingSingeModulePerGradleProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "processResources {\n" +
      "  include '**/*.yyy'\n" +
      "  include '**/*.xxx'\n" +
      "  exclude 'dir/*.yyy'\n" +
      "  exclude '*.xxx'\n" +
      "}\n" +
      "\n" +
      "processTestResources {\n" +
      "  include '**/*.yyy'\n" +
      "  include '**/*.xxx'\n" +
      "  exclude 'dir/*.yyy'\n" +
      "  exclude '*.xxx'\n" +
      "}\n"
    );
    assertModules("project");
    compileModules("project");

    assertCopiedResources();
  }

  @Test
  public void testModuleWithNameTestResourceCopying() throws Exception {
    createProjectSubFile("bar/foo/src/main/resources/dir/file.properties");
    createProjectSubFile("bar/foo/src/test/resources/dir/file-test.properties");
    createProjectSubFile("bar/test/src/main/resources/dir/file.properties");
    createProjectSubFile("bar/test/src/test/resources/dir/file-test.properties");
    createSettingsFile("include ':bar:foo'\n" +
                       "include ':bar:test'");

    importProjectUsingSingeModulePerGradleProject(
      "subprojects {\n" +
      "  apply plugin: 'java'\n" +
      "}\n"
    );
    assertModules("project", "bar", "foo", "test");
    compileModules("project", "bar", "foo", "test");

    assertCopied("bar/foo/build/resources/main/dir/file.properties");
    assertCopied("bar/foo/build/resources/test/dir/file-test.properties");
    assertCopied("bar/test/build/resources/main/dir/file.properties");
    assertCopied("bar/test/build/resources/test/dir/file-test.properties");
  }

  private void createFilesForIncludesAndExcludesTest() throws IOException {
    createProjectSubFile("src/main/resources/dir/file.xxx");
    createProjectSubFile("src/main/resources/dir/file.yyy");
    createProjectSubFile("src/main/resources/file.xxx");
    createProjectSubFile("src/main/resources/file.yyy");
    createProjectSubFile("src/main/resources/file.zzz");

    createProjectSubFile("src/test/resources/dir/file.xxx");
    createProjectSubFile("src/test/resources/dir/file.yyy");
    createProjectSubFile("src/test/resources/file.xxx");
    createProjectSubFile("src/test/resources/file.yyy");
    createProjectSubFile("src/test/resources/file.zzz");
  }

  private void assertCopiedResources() {
    // assert production resources
    assertCopied("build/resources/main/dir/file.xxx");
    assertCopied("build/resources/main/file.yyy");
    assertNotCopied("build/resources/main/dir/file.yyy");
    assertNotCopied("build/resources/main/file.xxx");
    assertNotCopied("build/resources/main/file.zzz");

    // assert test resources
    assertCopied("build/resources/test/dir/file.xxx");
    assertCopied("build/resources/test/file.yyy");
    assertNotCopied("build/resources/test/dir/file.yyy");
    assertNotCopied("build/resources/test/file.xxx");
    assertNotCopied("build/resources/test/file.zzz");
  }
}
