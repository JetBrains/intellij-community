/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.ant;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderTestCase;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.intellij.util.io.TestFileSystemBuilder.fs;

/**
 * @author nik
 */
public class JpsAntArtifactBuilderTaskTest extends ArtifactBuilderTestCase {
  private File myArtifactsOutput;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myArtifactsOutput = FileUtil.createTempDirectory("artifactsOutput", null);
  }

  public void testSimple() throws IOException {
    JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(myModel.getGlobal()).addPathVariable(PathMacroUtil.APPLICATION_HOME_DIR, PathManager.getHomePath());
    JpsGlobalLoader.loadGlobalSettings(myModel.getGlobal(), getTestDataRootPath() + "/config/options");
    addJdk("1.6");
    loadProject("ant-project");
    rebuildAll();

    assertOutput(new File(myArtifactsOutput, "dir").getAbsolutePath(),
                 fs().file("file.txt").file("echo.txt", "post"));
    assertOutput(new File(myArtifactsOutput, "jar").getAbsolutePath(),
                 fs().archive("jar.jar").file("file.txt").file("echo.txt", "post"));
  }

  @Nullable
  @Override
  protected String getTestDataRootPath() {
    return PathManagerEx.findFileUnderProjectHome("plugins/ant/jps-plugin/testData", getClass()).getAbsolutePath();
  }

  @NotNull
  @Override
  protected Map<String, String> getAdditionalPathVariables() {
    return Collections.singletonMap("ARTIFACTS_OUT", FileUtil.toSystemIndependentName(myArtifactsOutput.getAbsolutePath()));
  }
}
