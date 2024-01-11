// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Vladislav.Soroka
 */
public class ModelBuildScriptClasspathBuilderImplTest extends AbstractModelBuilderTest {

  public ModelBuildScriptClasspathBuilderImplTest(@NotNull String gradleVersion) {
    super(gradleVersion);
  }

  @Test
  public void testModelBuildScriptClasspathBuilder() {

    DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getModel(IdeaProject.class).getModules();

    List<BuildScriptClasspathModel> ideaModule =
      ContainerUtil.mapNotNull(ideaModules, module -> {
        BuildScriptClasspathModel classpathModel = allModels.getModel(module, BuildScriptClasspathModel.class);

        if (module.getName().equals("moduleWithAdditionalClasspath")) {
          assertNotNull(classpathModel);
          assertEquals(3, classpathModel.getClasspath().size());

          assertEquals("junit-4.11.jar", new File(classpathModel.getClasspath().getAt(0).getClasses().iterator().next()).getName());
          assertEquals("hamcrest-core-1.3.jar",
                       new File(classpathModel.getClasspath().getAt(1).getClasses().iterator().next()).getName());
          assertEquals("someDep.jar", new File(classpathModel.getClasspath().getAt(2).getClasses().iterator().next()).getName());
        }
        else if (module.getName().equals("baseModule") ||
                 module.getName().equals("moduleWithInheritedClasspath")) {
          assertNotNull("Null build classpath for module: " + module.getName(), classpathModel);
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
          assertNotNull("Wrong build classpath for module: " + module.getName(), classpathModel);
          assertEquals("Wrong build classpath for module: " + module.getName(), 2, classpathModel.getClasspath().size());
        }
        else {
          fail("Unexpected module found: " + module.getName());
        }

        return classpathModel;
      });

    assertEquals(5, ideaModule.size());
  }

  @Override
  protected Set<Class<?>> getModels() {
    return Collections.singleton(BuildScriptClasspathModel.class);
  }
}
