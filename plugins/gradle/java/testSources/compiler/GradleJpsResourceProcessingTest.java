// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.compiler;

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Vladislav.Soroka
 */
public class GradleJpsResourceProcessingTest extends GradleJpsCompilingTestCase {

  @Test
  public void testBasicResourceCopying() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    importProject(
      "apply plugin: 'java'"
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main", "project.test");

    assertCopied("out/production/resources/dir/file.properties");
    assertCopied("out/test/resources/dir/file-test.properties");
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

    assertCopied("out/production/resources/dir/file.properties");
    assertCopied("out/test/resources/dir/file-test.properties");
  }

  @Test
  public void testResourceCopyingFromSourcesFolder() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    createProjectSubFile("src/main/java/file.txt");
    importProject(
      """
        apply plugin: 'java'
        sourceSets {
          main {
            resources.srcDir file('src/main/java')
          }
        }"""
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main", "project.test");

    assertCopied("out/production/resources/dir/file.properties");
    assertCopied("out/test/resources/dir/file-test.properties");
    assertCopied("out/production/resources/file.txt");
  }

  @Test
  public void testResourceCopyingFromSourcesFolder_MergedProject() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    createProjectSubFile("src/main/java/file.txt");
    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'
        sourceSets {
          main {
            resources.srcDir file('src/main/java')
          }
        }"""
    );
    assertModules("project");
    compileModules("project");

    assertCopied("out/production/resources/dir/file.properties");
    assertCopied("out/test/resources/dir/file-test.properties");
    assertCopied("out/production/resources/file.txt");
  }

  @Test
  public void testResourceProcessingWithIdeaPluginCustomization() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    importProject(
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        idea {
          module {
            inheritOutputDirs = false
            outputDir = file('muchBetterOutputDir')
            testOutputDir = file('muchBetterTestOutputDir')
          }
        }"""
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main", "project.test");

    assertCopied("muchBetterOutputDir/dir/file.properties");
    assertCopied("muchBetterTestOutputDir/dir/file-test.properties");
  }

  @Test
  public void testResourceProcessingWithIdeaPluginCustomization_Merged() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");
    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        idea {
          module {
            inheritOutputDirs = false
            outputDir = file('muchBetterOutputDir')
            testOutputDir = file('muchBetterTestOutputDir')
          }
        }"""
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
      """
        apply plugin: 'java'

        sourceSets {
          main {
            resources {
              include '**/*.yyy'
              include '**/*.xxx'
              exclude 'dir/*.yyy'
              exclude '*.xxx'
            }
          }
          test {
            resources {
              include '**/*.yyy'
              include '**/*.xxx'
              exclude 'dir/*.yyy'
              exclude '*.xxx'
            }
          }
        }"""
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main", "project.test");

    assertCopiedResources();
  }

  @Test
  public void testIncludesAndExcludesInSourceSets_MergedProject() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'

        sourceSets {
          main {
            resources {
              include '**/*.yyy'
              include '**/*.xxx'
              exclude 'dir/*.yyy'
              exclude '*.xxx'
            }
          }
          test {
            resources {
              include '**/*.yyy'
              include '**/*.xxx'
              exclude 'dir/*.yyy'
              exclude '*.xxx'
            }
          }
        }"""
    );
    assertModules("project");
    compileModules("project");

    assertCopiedResources();
  }

  @Test
  public void testIncludesAndExcludesInAllSourceSets() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProject(
      """
        apply plugin: 'java'

        sourceSets.all {
          resources {
            include '**/*.yyy'
            include '**/*.xxx'
            exclude 'dir/*.yyy'
            exclude '*.xxx'
          }
        }"""
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main", "project.test");

    assertCopiedResources();
  }

  @Test
  public void testIncludesAndExcludesInAllSourceSets_MergedProject() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'

        sourceSets.all {
          resources {
            include '**/*.yyy'
            include '**/*.xxx'
            exclude 'dir/*.yyy'
            exclude '*.xxx'
          }
        }"""
    );
    assertModules("project");
    compileModules("project");

    assertCopiedResources();
  }

  @Test
  public void testIncludesAndExcludesInResourcesTask() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProject(
      """
        apply plugin: 'java'

        processResources {
          include '**/*.yyy'
          include '**/*.xxx'
          exclude 'dir/*.yyy'
          exclude '*.xxx'
        }

        processTestResources {
          include '**/*.yyy'
          include '**/*.xxx'
          exclude 'dir/*.yyy'
          exclude '*.xxx'
        }
        """
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main", "project.test");

    assertCopiedResources();
  }

  @Test
  public void testIncludesAndExcludesInResourcesTask_MergedProject() throws Exception {
    createFilesForIncludesAndExcludesTest();

    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'

        processResources {
          include '**/*.yyy'
          include '**/*.xxx'
          exclude 'dir/*.yyy'
          exclude '*.xxx'
        }

        processTestResources {
          include '**/*.yyy'
          include '**/*.xxx'
          exclude 'dir/*.yyy'
          exclude '*.xxx'
        }
        """
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
      """
        subprojects {
          apply plugin: 'java'
        }
        """
    );
    assertModules("project", "project.bar", "project.bar.foo", "project.bar.test");
    compileModules("project", "project.bar", "project.bar.foo", "project.bar.test");

    assertCopied("bar/foo/out/production/resources/dir/file.properties");
    assertCopied("bar/foo/out/test/resources/dir/file-test.properties");
    assertCopied("bar/test/out/production/resources/dir/file.properties");
    assertCopied("bar/test/out/test/resources/dir/file-test.properties");
  }

  @Test
  public void testSourceSetCompilationDefault() throws Exception {
    createProjectSubFile("src/main/java/App.java", "public class App {}");
    createProjectSubFile("src/main/resources/dir/file.properties");

    createProjectSubFile("src/test/java/Test.java", "public class Test {}");
    createProjectSubFile("src/test/resources/dir/file-test.properties");

    createProjectSubFile("src/integrationTest/java/IntegrationTest.java", "public class IntegrationTest {}");
    createProjectSubFile("src/integrationTest/resources/dir/file-integrationTest.properties");

    importProject(
      """
        apply plugin: 'java'

        sourceSets {
          integrationTest
        }
        """
    );
    assertModules("project", "project.main", "project.test", "project.integrationTest");

    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
    assertSourceRoots("project.integrationTest", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/integrationTest/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/integrationTest/resources"))
    );

    compileModules("project.main", "project.test", "project.integrationTest");

    assertCopied("out/production/classes/App.class");
    assertCopied("out/production/resources/dir/file.properties");
    assertCopied("out/test/resources/dir/file-test.properties");
    assertCopied("out/test/classes/Test.class");
    assertCopied("out/integrationTest/resources/dir/file-integrationTest.properties");
    assertCopied("out/integrationTest/classes/IntegrationTest.class");
  }

  @Test
  public void testSourceSetCompilationCustomOut() throws Exception {
    createProjectSubFile("src/main/java/App.java", "public class App {}");
    createProjectSubFile("src/main/resources/dir/file.properties");

    createProjectSubFile("src/test/java/Test.java", "public class Test {}");
    createProjectSubFile("src/test/resources/dir/file-test.properties");

    createProjectSubFile("src/integrationTest/java/IntegrationTest.java", "public class IntegrationTest {}");
    createProjectSubFile("src/integrationTest/resources/dir/file-integrationTest.properties");

    importProject(
      """
        apply plugin: 'java'

        sourceSets {
          integrationTest
        }

        apply plugin: 'idea'
        idea {
          module {
            inheritOutputDirs = false
            outputDir = project.file('muchBetterOutputDir')
          }
        }"""
    );
    assertModules("project", "project.main", "project.test", "project.integrationTest");

    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
    assertSourceRoots("project.integrationTest", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/integrationTest/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/integrationTest/resources"))
    );

    compileModules("project.main", "project.test", "project.integrationTest");

    assertCopied("muchBetterOutputDir/App.class");
    assertCopied("muchBetterOutputDir/dir/file.properties");

    assertCopied("out/test/classes/Test.class");
    assertCopied("out/test/resources/dir/file-test.properties");

    assertCopied("muchBetterOutputDir/IntegrationTest.class");
    assertCopied("muchBetterOutputDir/dir/file-integrationTest.properties");
  }

  @Test
  public void testSourceSetCompilationCustomTestOut() throws Exception {
    createProjectSubFile("src/main/java/App.java", "public class App {}");
    createProjectSubFile("src/main/resources/dir/file.properties");

    createProjectSubFile("src/test/java/Test.java", "public class Test {}");
    createProjectSubFile("src/test/resources/dir/file-test.properties");

    createProjectSubFile("src/integrationTest/java/IntegrationTest.java", "public class IntegrationTest {}");
    createProjectSubFile("src/integrationTest/resources/dir/file-integrationTest.properties");

    importProject(
      """
        apply plugin: 'java'

        sourceSets {
          integrationTest
        }

        apply plugin: 'idea'
        idea {
          module {
            inheritOutputDirs = false
            testOutputDir = project.file('muchBetterTestOutputDir')
          }
        }"""
    );
    assertModules("project", "project.main", "project.test", "project.integrationTest");

    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
    assertSourceRoots("project.integrationTest", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/integrationTest/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/integrationTest/resources"))
    );

    compileModules("project.main", "project.test", "project.integrationTest");

    assertCopied("out/production/classes/App.class");
    assertCopied("out/production/resources/dir/file.properties");
    assertCopied("muchBetterTestOutputDir/dir/file-test.properties");
    assertCopied("muchBetterTestOutputDir/Test.class");
    assertCopied("out/integrationTest/resources/dir/file-integrationTest.properties");
    assertCopied("out/integrationTest/classes/IntegrationTest.class");
  }

  @Test
  public void testTestRelatedSourceSetCompilation() throws Exception {
    createProjectSubFile("src/main/java/App.java", "public class App {}");
    createProjectSubFile("src/main/resources/dir/file.properties");

    createProjectSubFile("src/test/java/Test.java", "public class Test {}");
    createProjectSubFile("src/test/resources/dir/file-test.properties");

    createProjectSubFile("src/integrationTest/java/IntegrationTest.java", "public class IntegrationTest {}");
    createProjectSubFile("src/integrationTest/resources/dir/file-integrationTest.properties");

    String testRoots = isGradleAtLeast("7.4")
                       ? """
                       testSources.from(project.sourceSets.integrationTest.java.srcDirs)
                       testSources.from(project.sourceSets.integrationTest.resources.srcDirs)
                       """
                       : """
                       testSourceDirs += project.sourceSets.integrationTest.java.srcDirs
                       testSourceDirs += project.sourceSets.integrationTest.resources.srcDirs
                       """;

    importProject(
      """
        apply plugin: 'java'

        sourceSets {
          integrationTest
        }

        apply plugin: 'idea'
        idea {
          module {
            inheritOutputDirs = false
            %s
          }
        }""".formatted(testRoots)
    );
    assertModules("project", "project.main", "project.test", "project.integrationTest");

    assertSourceRoots("project.main", it -> it
      .sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      .sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    );
    assertSourceRoots("project.test", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    );
    assertSourceRoots("project.integrationTest", it -> it
      .sourceRoots(ExternalSystemSourceType.TEST, path("src/integrationTest/java"))
      .sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/integrationTest/resources"))
    );

    compileModules("project.main", "project.test", "project.integrationTest");

    assertCopied("out/production/classes/App.class");
    assertCopied("out/production/resources/dir/file.properties");
    assertCopied("out/test/resources/dir/file-test.properties");
    assertCopied("out/test/classes/Test.class");
    assertCopied("out/integrationTest/resources/dir/file-integrationTest.properties");
    assertCopied("out/integrationTest/classes/IntegrationTest.class");
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
    assertCopied("out/production/resources/dir/file.xxx");
    assertCopied("out/production/resources/file.yyy");
    assertNotCopied("out/production/resources/dir/file.yyy");
    assertNotCopied("out/production/resources/file.xxx");
    assertNotCopied("out/production/resources/file.zzz");

    // assert test resources
    assertCopied("out/test/resources/dir/file.xxx");
    assertCopied("out/test/resources/file.yyy");
    assertNotCopied("out/test/resources/dir/file.yyy");
    assertNotCopied("out/test/resources/file.xxx");
    assertNotCopied("out/test/resources/file.zzz");
  }
}
