// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder;
import org.jetbrains.plugins.gradle.model.scala.ScalaCompileOptions;
import org.jetbrains.plugins.gradle.model.scala.ScalaModel;
import org.jetbrains.plugins.gradle.service.modelAction.GradleIdeaModelHolder;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;

import java.io.File;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Vladislav.Soroka
 */
public class ScalaModelBuilderImplTest extends AbstractModelBuilderTest {

  public ScalaModelBuilderImplTest(@NotNull String gradleVersion) {
    super(gradleVersion);
  }

  @Test
  public void testScalaModel() {
    createProjectFile("build.gradle", GradleBuildScriptBuilder.create(gradleVersion, false)
      .applyPlugin("scala")
      .withMavenCentral()
      .addImplementationDependency("org.scala-lang:scala-library:2.11.0")
      .addPostfix(
        "Closure compilerPlugins = {\n" +
        "  String parjar = \"opt\"\n" +
        "  scalaCompileOptions.additionalParameters = [\n" +
        "    \"-opt:$parjar\"\n" +
        "  ]\n" +
        "}\n" +
        "compileScala compilerPlugins\n" +
        "compileTestScala compilerPlugins"
      )
      .generate()
    );

    GradleIdeaModelHolder models = runBuildAction(ScalaModel.class);

    DomainObjectSet<? extends IdeaModule> ideaModules = models.getRootModel(IdeaProject.class).getModules();
    assertEquals(1, ideaModules.size());
    IdeaModule ideaModule = ideaModules.iterator().next();

    ScalaModel scalaModel = models.getProjectModel(ideaModule, ScalaModel.class);
    ScalaCompileOptions scalaCompileOptions = scalaModel.getScalaCompileOptions();
    assertNotNull(scalaCompileOptions);
    assertEquals(1, scalaCompileOptions.getAdditionalParameters().size());
    assertEquals("-opt:opt", scalaCompileOptions.getAdditionalParameters().iterator().next());
  }

  @Test
  @TargetVersions("6.4+")
  public void testScalaModelWithScalaCompilerPlugins() {
    createProjectFile("build.gradle", GradleBuildScriptBuilder.create(gradleVersion, false)
      .applyPlugin("scala")
      .withMavenCentral()
      .addImplementationDependency("org.scala-lang:scala-library:2.13.14")
      .addDependency("scalaCompilerPlugins", "com.olegpy:better-monadic-for_2.13:0.3.1")
      .generate()
    );

    GradleIdeaModelHolder models = runBuildAction(ScalaModel.class);

    DomainObjectSet<? extends IdeaModule> ideaModules = models.getRootModel(IdeaProject.class).getModules();
    assertEquals(1, ideaModules.size());
    IdeaModule ideaModule = ideaModules.iterator().next();

    ScalaModel scalaModel = models.getProjectModel(ideaModule, ScalaModel.class);
    Set<File> scalaCompilerPlugins = scalaModel.getScalaCompilerPlugins();
    assertNotNull(scalaCompilerPlugins);
    assertEquals(1, scalaCompilerPlugins.size());
    String compilerPluginPath = scalaCompilerPlugins.iterator().next().getPath();
    assertTrue(compilerPluginPath.endsWith("better-monadic-for_2.13-0.3.1.jar"));
  }
}
