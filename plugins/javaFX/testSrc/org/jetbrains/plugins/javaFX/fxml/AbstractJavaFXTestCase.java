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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.packaging.JavaFxApplicationArtifactType;

import java.io.File;

/**
 * User: anna
 * Date: 3/20/13
 */
public abstract class AbstractJavaFXTestCase extends LightCodeInsightFixtureTestCase {
  public static final DefaultLightProjectDescriptor JAVA_FX_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      addJavaFxJarAsLibrary(module, model);
      ArtifactManager.getInstance(model.getProject()).addArtifact("fake-javafx", JavaFxApplicationArtifactType.getInstance(), null);
      super.configureModule(module, model, contentEntry);
    }
  };

  public static void addJavaFxJarAsLibrary(@NotNull Module module) {
    addJavaFxJarAsLibrary(module, null);
  }

  public static void addJavaFxJarAsLibrary(@NotNull Module module, @Nullable ModifiableRootModel model) {
    final String libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "ext";
    if (model != null) {
      PsiTestUtil.addLibrary(module, model, "javafx", libPath, "jfxrt.jar");
    }
    else {
      PsiTestUtil.addLibrary(module, "javafx", libPath, "jfxrt.jar");
    }
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
    enableInspections();
  }
}
