/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.packaging.JavaFxApplicationArtifactType;

public abstract class AbstractJavaFXTestCase extends LightJavaCodeInsightFixtureTestCase {
  public static final DefaultLightProjectDescriptor JAVA_FX_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      addJavaFxJarAsLibrary(model);
      ArtifactManager.getInstance(model.getProject()).addArtifact("fake-javafx", JavaFxApplicationArtifactType.getInstance(), null);
      super.configureModule(module, model, contentEntry);
    }
  };

  public static void addJavaFxJarAsLibrary(@NotNull Module module) {
    ModuleRootModificationUtil.updateModel(module, m -> addJavaFxJarAsLibrary(m));
  }

  public static void addJavaFxJarAsLibrary(@Nullable ModifiableRootModel model) {
    MavenDependencyUtil.addFromMaven(model, "org.openjfx:javafx-fxml:11.0.1");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_FX_DESCRIPTOR;
  }

  protected void enableInspections() {}
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new XmlUnresolvedReferenceInspection());
    enableInspections();
  }
}
