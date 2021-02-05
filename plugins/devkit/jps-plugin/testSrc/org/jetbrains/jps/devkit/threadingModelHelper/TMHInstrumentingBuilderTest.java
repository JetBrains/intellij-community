// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.devkit.threadingModelHelper;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;

public class TMHInstrumentingBuilderTest extends JpsBuildTestCase {
  private static final String TEST_DATA_PATH = "plugins/devkit/jps-plugin/testData/threadingModelHelper/builder/";
  private static final String DEPENDENCIES_PATH = TEST_DATA_PATH + "dependencies";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBuildParams.put(TMHInstrumentingBuilder.INSTRUMENT_ANNOTATIONS_PROPERTY, "true");
  }

  public void testSimple() throws IOException {
    String src = copyToProject(DEPENDENCIES_PATH, "src");
    String testFile = copyToProject(TEST_DATA_PATH + getTestName(false) + ".java",
                                    "src/" + TEST_DATA_PATH + getTestName(false) + ".java");
    JpsModule m = addModule("m", src, testFile);
    buildAllModules().assertSuccessful();

    assertTrue(TMHTestUtil.containsMethodCall(FileUtil.loadFileBytes(getActualFile(m)), "assertIsDispatchThread"));
  }

  @NotNull
  private File getActualFile(@NotNull JpsModule m) {
    File outputDirectory = JpsJavaExtensionService.getInstance().getOutputDirectory(m, false);
    File file = new File(outputDirectory, getTestName(false) + ".class");
    assertTrue(file.getAbsolutePath() + " not found", file.exists());
    return file;
  }
}
