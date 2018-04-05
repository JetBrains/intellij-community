// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

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

  @Test
  public void testBuildSrcClassesUsages() throws Exception {
    createProjectSubFile("settings.gradle", "rootProject.name = 'multiproject'\n" +
                                            "include ':app'");

    createProjectSubFile("buildSrc/src/main/groovy/org/buildsrc/BuildSrcClass.groovy", "package org.buildsrc;\n" +
                                                                                       "public class BuildSrcClass {}");
    createProjectSubFile("app/build.gradle", "def foo = new org.buildsrc.BuildSrcClass()");

    importProject();
    assertModules("multiproject", "app",
                  "buildSrc", "buildSrc_main", "buildSrc_test");

    Module buildSrcModule = getModule("buildSrc_main");
    assertNotNull(buildSrcModule);
    edt(() -> {
      PsiClass[] buildSrcClasses =
        JavaPsiFacade.getInstance(myProject).findClasses("org.buildsrc.BuildSrcClass", GlobalSearchScope.moduleScope(buildSrcModule));
      assertEquals(1, buildSrcClasses.length);

      assertUsagesCount(1, buildSrcClasses[0]);
    });
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
                  "buildSrc", "buildSrc_main", "buildSrc_test",
                  "buildSrcSubProject", "buildSrcSubProject_main", "buildSrcSubProject_test");

    Module buildSrcModule = getModule("buildSrc_main");
    assertNotNull(buildSrcModule);
    Module buildSrcSubModule = getModule("buildSrcSubProject_main");
    assertNotNull(buildSrcSubModule);
    assertUsages(buildSrcModule, buildSrcSubModule);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("multiproject", "app",
                  "buildSrc",
                  "buildSrcSubProject");

    buildSrcModule = getModule("buildSrc");
    assertNotNull(buildSrcModule);
    buildSrcSubModule = getModule("buildSrcSubProject");
    assertNotNull(buildSrcSubModule);
    assertUsages(buildSrcModule, buildSrcSubModule);
  }

  private void assertUsages(Module module1, Module module2) {
    edt(() -> {
      PsiClass[] buildSrcClasses =
        JavaPsiFacade.getInstance(myProject).findClasses("org.buildsrc.BuildSrcClass", GlobalSearchScope.moduleScope(module1));
      assertEquals(1, buildSrcClasses.length);

      assertUsagesCount(2, buildSrcClasses[0]);

      PsiClass[] buildSrcAdditionalClasses =
        JavaPsiFacade.getInstance(myProject)
                     .findClasses("org.buildsrc.BuildSrcAdditionalClass", GlobalSearchScope.moduleScope(module2));
      assertEquals(1, buildSrcAdditionalClasses.length);

      assertUsagesCount(1, buildSrcAdditionalClasses[0]);
    });
  }

  private static void assertUsagesCount(int expectedUsagesCount, PsiElement resolved) throws Exception {
    assertEquals(expectedUsagesCount, doFindUsages(resolved).size());
  }

  private static Collection<UsageInfo> doFindUsages(PsiElement resolved) throws Exception {
    return ProgressManager.getInstance().run(new Task.WithResult<Collection<UsageInfo>, Exception>(resolved.getProject(), "", false) {
      @Override
      protected Collection<UsageInfo> compute(@NotNull ProgressIndicator indicator) {
        return ApplicationManager.getApplication().runReadAction((Computable<Collection<UsageInfo>>)() -> {
          FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(resolved.getProject())).getFindUsagesManager();
          FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(resolved, false);
          assertNotNull(handler);
          final FindUsagesOptions options = handler.getFindUsagesOptions();
          final CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<UsageInfo>();
          for (PsiElement element : handler.getPrimaryElements()) {
            handler.processElementUsages(element, processor, options);
          }
          for (PsiElement element : handler.getSecondaryElements()) {
            handler.processElementUsages(element, processor, options);
          }
          return processor.getResults();
        });
      }
    });
  }
}
