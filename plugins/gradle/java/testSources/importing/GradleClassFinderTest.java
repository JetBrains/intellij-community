// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
public class GradleClassFinderTest extends GradleImportingTestCase {

  /**
   * It's sufficient to run the test against one gradle version
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
  }

  @Override
  public void assumeGradleVersion() { }

  @Test
  public void testClassesFilter() throws Exception {
    createProjectSubFile("settings.gradle", "rootProject.name = 'multiproject'\n" +
                                            "include ':app'");

    // app module files
    createProjectSubFile("app/src/main/groovy/App.groovy", "class App {}");
    // buildSrc module files
    createProjectSubFile("buildSrc/src/main/groovy/org/buildsrc/BuildSrcClass.groovy", """
      package org.buildsrc;
      public class BuildSrcClass {}""");
    importProject("""
                    subprojects {
                        apply plugin: 'groovy'
                    }""");
    assertModules("multiproject",
                  "multiproject.app", "multiproject.app.main", "multiproject.app.test",
                  "multiproject.buildSrc", "multiproject.buildSrc.main", "multiproject.buildSrc.test");
    Module buildSrcModule = getModule("multiproject.buildSrc.main");
    assertNotNull(buildSrcModule);
    ApplicationManager.getApplication().runReadAction(() -> {
      PsiClass[] appClasses = JavaPsiFacade.getInstance(getMyProject()).findClasses("App", GlobalSearchScope.allScope(getMyProject()));
      assertEquals(1, appClasses.length);

      PsiClass[] buildSrcClasses =
        JavaPsiFacade.getInstance(getMyProject()).findClasses("org.buildsrc.BuildSrcClass", GlobalSearchScope.moduleScope(buildSrcModule));
      assertEquals(1, buildSrcClasses.length);
    });
  }
}
