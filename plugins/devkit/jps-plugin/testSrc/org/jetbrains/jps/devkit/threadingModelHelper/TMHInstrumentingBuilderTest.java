// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.threadingModelHelper;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;

public class TMHInstrumentingBuilderTest extends JpsBuildTestCase {

  private static final String TEST_DATA_PATH = "plugins/devkit/jps-plugin/testData/threadingModelHelper/builder/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBuildParams.put(TMHInstrumentingBuilder.INSTRUMENT_ANNOTATIONS_PROPERTY, "true");
  }

  public void testSimple1() throws IOException {
    doSimpleTest("assertIsDispatchThread", "dependencies1");
  }

  public void testSimple2() throws IOException {
    doSimpleTest("assertEventDispatchThread", "dependencies2");
  }

  private void doSimpleTest(String assertIsDispatchThread, String dependencyPath) throws IOException {
    String src = copyToProject(TEST_DATA_PATH + dependencyPath, "src");
    String testFileName = "Simple.java";
    String testFile = copyToProject(TEST_DATA_PATH + testFileName,
                                    "src/" + TEST_DATA_PATH + testFileName);
    JpsModule m = addModule("m", src, testFile);
    buildAllModules().assertSuccessful();

    File outputDirectory = JpsJavaExtensionService.getInstance().getOutputDirectory(m, false);
    File file = new File(outputDirectory, "Simple.class");
    assertTrue(file.getAbsolutePath() + " not found", file.exists());
    assertTrue(TMHTestUtil.containsMethodCall(FileUtil.loadFileBytes(file), assertIsDispatchThread));
  }
}
