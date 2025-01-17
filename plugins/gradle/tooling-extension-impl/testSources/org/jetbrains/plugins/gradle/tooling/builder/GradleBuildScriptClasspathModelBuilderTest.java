// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.gradle.toolingExtension.impl.model.buildScriptClasspathModel.GradleBuildScriptClasspathModelProvider;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder;
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder;
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.service.modelAction.GradleIdeaModelHolder;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * @author Vladislav.Soroka
 */
public class GradleBuildScriptClasspathModelBuilderTest extends AbstractModelBuilderTest {

  public GradleBuildScriptClasspathModelBuilderTest(@NotNull String gradleVersion) {
    super(gradleVersion);
  }

  @Test
  public void testModelBuildScriptClasspathBuilder() {
    createProjectFile("settings.gradle", GradleSettingScriptBuilder.create(gradleVersion, false)
      .include("moduleWithoutAdditionalClasspath")
      .include("moduleWithAdditionalClasspath")
      .include("baseModule")
      .include("baseModule:moduleWithInheritedClasspath")
      .generate()
    );
    createProjectFile("build.gradle", GradleBuildScriptBuilder.create(gradleVersion, false)
      .withBuildScriptMavenCentral()
      .addBuildScriptClasspath("junit:junit:4.11")
      .addPostfix(
        "allprojects {\n" +
        "  configurations.all {\n" +
        "    // check for configuration which is not in unresolved state - https://youtrack.jetbrains.com/issue/IDEA-124839\n" +
        "    exclude group: 'some-group'\n" +
        "    // check for the usage of custom resolutionStrategy - https://youtrack.jetbrains.com/issue/IDEA-125592\n" +
        "    resolutionStrategy.eachDependency { DependencyResolveDetails details ->\n" +
        "      println details.target\n" +
        "    }\n" +
        "  }\n" +
        "}"
      )
      .generate()
    );
    createProjectFile("moduleWithoutAdditionalClasspath/build.gradle", "");
    createProjectFile("baseModule/moduleWithInheritedClasspath/build.gradle", "");
    createProjectFile("moduleWithAdditionalClasspath/build.gradle",
                      "buildscript {\n" +
                      "  dependencies {\n" +
                      "    classpath files(\"lib/someDep.jar\")\n" +
                      "  }\n" +
                      "}");
    createProjectFile("baseModule/build.gradle",
                      "buildscript {\n" +
                      "  dependencies {\n" +
                      "    classpath files(\"lib/inheritedDep.jar\")\n" +
                      "  }\n" +
                      "}");

    GradleIdeaModelHolder models = runBuildAction(new GradleBuildScriptClasspathModelProvider());

    DomainObjectSet<? extends IdeaModule> ideaModules = models.getRootModel(IdeaProject.class).getModules();
    assertEquals(5, ideaModules.size());

    for (IdeaModule module : ideaModules) {
      GradleBuildScriptClasspathModel classpathModel = models.getProjectModel(module, GradleBuildScriptClasspathModel.class);
      assertNotNull("Null build classpath for module: " + module.getName(), classpathModel);

      if (module.getName().equals("moduleWithAdditionalClasspath")) {
        assertEquals("Wrong build classpath for module: " + module.getName(), 3, classpathModel.getClasspath().size());
        assertEquals("Wrong build classpath for module: " + module.getName(), "junit-4.11.jar",
                     new File(classpathModel.getClasspath().getAt(0).getClasses().iterator().next()).getName());
        assertEquals("Wrong build classpath for module: " + module.getName(), "hamcrest-core-1.3.jar",
                     new File(classpathModel.getClasspath().getAt(1).getClasses().iterator().next()).getName());
        assertEquals("Wrong build classpath for module: " + module.getName(), "someDep.jar",
                     new File(classpathModel.getClasspath().getAt(2).getClasses().iterator().next()).getName());
      }
      else if (module.getName().equals("baseModule") ||
               module.getName().equals("moduleWithInheritedClasspath")) {
        assertEquals("Wrong build classpath for module: " + module.getName(), 3, classpathModel.getClasspath().size());
        assertEquals("Wrong build classpath for module: " + module.getName(), "junit-4.11.jar",
                     new File(classpathModel.getClasspath().getAt(0).getClasses().iterator().next()).getName());
        assertEquals("Wrong build classpath for module: " + module.getName(), "hamcrest-core-1.3.jar",
                     new File(classpathModel.getClasspath().getAt(1).getClasses().iterator().next()).getName());
        assertEquals("Wrong build classpath for module: " + module.getName(), "inheritedDep.jar",
                     new File(classpathModel.getClasspath().getAt(2).getClasses().iterator().next()).getName());
      }
      else if (module.getName().equals("moduleWithoutAdditionalClasspath") ||
               module.getName().equals("testModelBuildScriptClasspathBuilder")) {
        assertEquals("Wrong build classpath for module: " + module.getName(), 2, classpathModel.getClasspath().size());
        assertEquals("Wrong build classpath for module: " + module.getName(), "junit-4.11.jar",
                     new File(classpathModel.getClasspath().getAt(0).getClasses().iterator().next()).getName());
        assertEquals("Wrong build classpath for module: " + module.getName(), "hamcrest-core-1.3.jar",
                     new File(classpathModel.getClasspath().getAt(1).getClasses().iterator().next()).getName());
      }
      else {
        fail("Unexpected module found: " + module.getName());
      }
    }
  }
}
