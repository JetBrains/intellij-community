// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.wrapper.PathAssembler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndGet;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleFindUsagesTest extends GradleImportingTestCase {

  /**
   * It's sufficient to run the test against one gradle version
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
  }

  @Override
  protected void collectAllowedRoots(List<String> roots, PathAssembler.LocalDistribution distribution) {
    File gradleUserHomeDir = new BuildLayoutParameters().getGradleUserHomeDir();
    File generatedGradleJarsDir = new File(gradleUserHomeDir, "caches/" + gradleVersion + "/generated-gradle-jars");
    roots.add(generatedGradleJarsDir.getPath());
    File gradleDistLibDir = new File(distribution.getDistributionDir(), "gradle-" + gradleVersion + "/lib");
    roots.add(gradleDistLibDir.getPath());
  }

  @Test
  public void testBuildSrcClassesUsages() throws Exception {
    createProjectSubFile("settings.gradle", "rootProject.name = 'multiproject'\n" +
                                            "include ':app'");

    createProjectSubFile("buildSrc/src/main/groovy/org/buildsrc/BuildSrcClass.groovy", "package org.buildsrc;\n" +
                                                                                       "public class BuildSrcClass {" +
                                                                                       "   public String sayHello() { 'Hello!' }" +
                                                                                       "}");
    createProjectSubFile("app/build.gradle", "def foo = new org.buildsrc.BuildSrcClass().sayHello()");

    importProject();
    assertModules("multiproject", "app",
                  "multiproject_buildSrc", "multiproject_buildSrc_main", "multiproject_buildSrc_test");

    Module buildSrcModule = getModule("multiproject_buildSrc_main");
    assertNotNull(buildSrcModule);
    assertUsages("org.buildsrc.BuildSrcClass", GlobalSearchScope.moduleScope(buildSrcModule), 1);
    assertUsages("org.buildsrc.BuildSrcClass", "sayHello", GlobalSearchScope.moduleScope(buildSrcModule), 1);
  }

  @Test
  public void testMultiModuleBuildSrcClassesUsages() throws Exception {
    createProjectSubFile("settings.gradle", "rootProject.name = 'multiproject'\n" +
                                            "include ':app'");
    // buildSrc module files
    createProjectSubFile("buildSrc/settings.gradle", "include 'buildSrcSubProject'");
    createProjectSubFile("buildSrc/build.gradle", "allprojects {\n" +
                                                  "    apply plugin: 'groovy'\n" +
                                                  "    dependencies {\n" +
                                                  "        compile gradleApi()\n" +
                                                  "        compile localGroovy()\n" +
                                                  "    }\n" +
                                                  "    repositories {\n" +
                                                  "        mavenCentral()\n" +
                                                  "    }\n" +
                                                  "\n" +
                                                  "    if (it != rootProject) {\n" +
                                                  "        rootProject.dependencies {\n" +
                                                  "            runtime project(path)\n" +
                                                  "        }\n" +
                                                  "    }\n" +
                                                  "}\n");
    createProjectSubFile("buildSrc/src/main/groovy/org/buildsrc/BuildSrcClass.groovy", "package org.buildsrc;\n" +
                                                                                       "public class BuildSrcClass {}");
    createProjectSubFile("buildSrc/buildSrcSubProject/src/main/java/org/buildsrc/BuildSrcAdditionalClass.java",
                         "package org.buildsrc;\n" +
                         "public class BuildSrcAdditionalClass {}");

    createProjectSubFile("build.gradle", "def foo = new org.buildsrc.BuildSrcClass()");
    createProjectSubFile("app/build.gradle", "def foo1 = new org.buildsrc.BuildSrcClass()\n" +
                                             "def foo2 = new org.buildsrc.BuildSrcAdditionalClass()");

    importProject();
    assertModules("multiproject", "app",
                  "multiproject_buildSrc", "multiproject_buildSrc_main", "multiproject_buildSrc_test",
                  "multiproject_buildSrcSubProject", "multiproject_buildSrcSubProject_main", "multiproject_buildSrcSubProject_test");

    assertUsages(pair("org.buildsrc.BuildSrcClass", 2), pair("org.buildsrc.BuildSrcAdditionalClass", 1));

    importProjectUsingSingeModulePerGradleProject();
    assertModules("multiproject", "app",
                  "multiproject_buildSrc",
                  "multiproject_buildSrcSubProject");

    assertUsages(pair("org.buildsrc.BuildSrcClass", 2), pair("org.buildsrc.BuildSrcAdditionalClass", 1));
  }

  @Test
  public void testIncludedBuildSrcClassesUsages() throws Exception {
    createProjectWithIncludedBuildAndBuildSrcModules();

    importProject();
    assertModules("multiproject", "app",
                  "multiproject_buildSrc", "multiproject_buildSrc_main", "multiproject_buildSrc_test",
                  "gradle-plugin", "gradle-plugin_test", "gradle-plugin_main",
                  "my.included_buildSrc", "my.included_buildSrc_main", "my.included_buildSrc_test");

    assertUsages("org.buildsrc.BuildSrcClass", 2);
    assertUsages("org.included.buildsrc.IncludedBuildSrcClass", 1);
    assertUsages("org.included.IncludedBuildClass", 2);
  }

  @Test
  public void testIncludedBuildSrcClassesUsages_merged() throws Exception {
    createProjectWithIncludedBuildAndBuildSrcModules();
    importProjectUsingSingeModulePerGradleProject();
    assertModules("multiproject", "app",
                  "multiproject_buildSrc",
                  "gradle-plugin",
                  "my.included_buildSrc");
    assertUsages(pair("org.buildsrc.BuildSrcClass", 2), pair("org.included.buildsrc.IncludedBuildSrcClass", 1));
    assertUsages("org.included.IncludedBuildClass", 2);
  }

  @Test
  public void testIncludedBuildSrcClassesUsages_qualified_names() throws Exception {
    createProjectWithIncludedBuildAndBuildSrcModules();
    // check for qualified module names
    getCurrentExternalProjectSettings().setUseQualifiedModuleNames(true);
    importProject();
    assertModules("multiproject", "multiproject.app",
                  "multiproject.buildSrc", "multiproject.buildSrc.main", "multiproject.buildSrc.test",
                  "my.included.gradle-plugin", "my.included.gradle-plugin.test", "my.included.gradle-plugin.main",
                  "my.included.buildSrc", "my.included.buildSrc.main", "my.included.buildSrc.test");
    assertUsages(pair("org.buildsrc.BuildSrcClass", 2), pair("org.included.buildsrc.IncludedBuildSrcClass", 1));
    assertUsages("org.included.IncludedBuildClass", 2);
  }

  @Test
  public void testIncludedBuildSrcClassesUsages_merged_qualified_names() throws Exception {
    createProjectWithIncludedBuildAndBuildSrcModules();
    // check for qualified module names
    getCurrentExternalProjectSettings().setUseQualifiedModuleNames(true);
    importProjectUsingSingeModulePerGradleProject();
    assertModules("multiproject", "multiproject.app",
                  "multiproject.buildSrc",
                  "my.included.gradle-plugin",
                  "my.included.buildSrc");
    assertUsages(pair("org.buildsrc.BuildSrcClass", 2), pair("org.included.buildsrc.IncludedBuildSrcClass", 1));
    assertUsages("org.included.IncludedBuildClass", 2);
  }

  private void createProjectWithIncludedBuildAndBuildSrcModules() throws IOException {
    createProjectSubFile("settings.gradle", "rootProject.name = 'multiproject'\n" +
                                            "include ':app'\n" +
                                            "includeBuild 'gradle-plugin'");
    createProjectSubFile("buildSrc/src/main/groovy/org/buildsrc/BuildSrcClass.groovy", "package org.buildsrc;\n" +
                                                                                       "public class BuildSrcClass {}");

    createProjectSubFile("build.gradle", "buildscript {\n" +
                                         "    dependencies {\n" +
                                         "        classpath 'my.included:gradle-plugin:0'\n" +
                                         "    }\n" +
                                         "}\n" +
                                         "def foo1 = new org.buildsrc.BuildSrcClass()\n" +
                                         "def foo2 = new org.included.IncludedBuildClass()");
    createProjectSubFile("app/build.gradle", "def foo1 = new org.buildsrc.BuildSrcClass()\n" +
                                             "def foo2 = new org.included.IncludedBuildClass()");

    // included build
    createProjectSubFile("gradle-plugin/settings.gradle", "");
    createProjectSubFile("gradle-plugin/build.gradle", "group 'my.included'\n" +
                                                       "apply plugin: 'java'\n" +
                                                       "def foo = new org.included.buildsrc.IncludedBuildSrcClass()");
    createProjectSubFile("gradle-plugin/buildSrc/src/main/groovy/org/included/buildsrc/IncludedBuildSrcClass.groovy",
                         "package org.included.buildsrc;\n" +
                         "public class IncludedBuildSrcClass {}");
    createProjectSubFile("gradle-plugin/src/main/java/org/included/IncludedBuildClass.java",
                         "package org.included;\n" +
                         "public class IncludedBuildClass {}");
  }

  private void assertUsages(String fqn, GlobalSearchScope scope, int count) throws Exception {
    assertUsages(fqn, null, scope, count);
  }

  private void assertUsages(@NotNull String fqn, @Nullable String methodName, GlobalSearchScope scope, int count) throws Exception {
    PsiClass[] psiClasses = runInEdtAndGet(() -> JavaPsiFacade.getInstance(myProject).findClasses(fqn, scope));
    assertEquals(1, psiClasses.length);
    PsiClass aClass = psiClasses[0];
    if (methodName != null) {
      PsiMethod[] methods = runInEdtAndGet(() -> aClass.findMethodsByName(methodName, false));
      int actualUsagesCount = 0;
      for (PsiMethod method : methods) {
        actualUsagesCount += findUsages(method).size();
      }
      assertEquals(count, actualUsagesCount);
    }
    else {
      assertUsagesCount(count, aClass);
    }
  }

  private void assertUsages(String fqn, int count) throws Exception {
    assertUsages(fqn, GlobalSearchScope.projectScope(myProject), count);
  }

  private void assertUsages(Trinity<String, GlobalSearchScope, Integer>... classUsageCount) throws Exception {
    for (Trinity<String, GlobalSearchScope, Integer> trinity : classUsageCount) {
      assertUsages(trinity.first, trinity.second, trinity.third);
    }
  }

  private void assertUsages(Pair<String, Integer>... classUsageCount) throws Exception {
    for (Pair<String, Integer> pair : classUsageCount) {
      assertUsages(Trinity.create(pair.first, GlobalSearchScope.projectScope(myProject), pair.second));
    }
  }

  private static void assertUsagesCount(int expectedUsagesCount, PsiElement resolved) throws Exception {
    assertEquals(expectedUsagesCount, findUsages(resolved).size());
  }
}
