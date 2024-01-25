// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.scala.ScalaCompileOptions;
import org.jetbrains.plugins.gradle.model.scala.ScalaModel;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Vladislav.Soroka
 */
public class ScalaModelBuilderImplTest extends AbstractModelBuilderTest {

  public ScalaModelBuilderImplTest(@NotNull String gradleVersion) {
    super(gradleVersion);
  }

  @Test
  public void testScalaModel() {
    DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getModel(IdeaProject.class).getModules();
    assertEquals(1, ideaModules.size());
    List<ScalaModel> scalaModels = ContainerUtil.mapNotNull(ideaModules, module -> allModels.getModel(module, ScalaModel.class));
    assertEquals(1, scalaModels.size());
    ScalaModel scalaModel = scalaModels.get(0);
    ScalaCompileOptions scalaCompileOptions = scalaModel.getScalaCompileOptions();
    assertNotNull(scalaCompileOptions);
    assertEquals(1, scalaCompileOptions.getAdditionalParameters().size());
    assertEquals("-opt:opt", scalaCompileOptions.getAdditionalParameters().iterator().next());
  }

  @Override
  protected Set<Class<?>> getModels() {
    return Collections.singleton(ScalaModel.class);
  }
}
