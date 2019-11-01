package com.intellij.jps.cache.loader;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.jps.cache.client.ArtifactoryJpsServerClient;
import com.intellij.jps.cache.model.AffectedModule;
import com.intellij.jps.cache.model.BuildTargetState;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class JpsCompilationOutputLoaderTest extends BasePlatformTestCase {
  private static final String PRODUCTION = "production";
  private static final String TEST = "test";
  private JpsCompilationOutputLoader compilationOutputLoader;
  private Type myTokenType;
  private Gson myGson;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    compilationOutputLoader = new JpsCompilationOutputLoader(ArtifactoryJpsServerClient.INSTANCE, getProject());
    myGson = new Gson();
    myTokenType = new TypeToken<Map<String, Map<String, BuildTargetState>>>() {}.getType();
  }

  public void testCurrentModelStateNull() throws IOException {
    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(getTestDataFile("caseOne.json")))) {
      Map<String, Map<String, BuildTargetState>> fileData = myGson.fromJson(bufferedReader, myTokenType);
      List<AffectedModule> affectedModules = compilationOutputLoader.getAffectedModules(null, fileData, false);
      assertSize(1243, affectedModules);
      // 836 production
      assertSize(836, ContainerUtil.filter(affectedModules, module -> module.getType().contains(PRODUCTION)));
      // 407 test
      assertSize(407, ContainerUtil.filter(affectedModules, module -> module.getType().contains(TEST)));
    }
  }

  public void testChangedSqlModule() throws IOException {
    try (BufferedReader bufferedReaderCurrentState = new BufferedReader(new FileReader(getTestDataFile("caseOne.json")));
         BufferedReader bufferedReaderCommitstate = new BufferedReader(new FileReader(getTestDataFile("caseTwo.json")))) {
      Map<String, Map<String, BuildTargetState>> currentState = myGson.fromJson(bufferedReaderCurrentState, myTokenType);
      Map<String, Map<String, BuildTargetState>> commitState = myGson.fromJson(bufferedReaderCommitstate, myTokenType);
      List<AffectedModule> affectedModules = compilationOutputLoader.getAffectedModules(currentState, commitState, false);
      assertSize(1, affectedModules);
      AffectedModule affectedModule = affectedModules.get(0);
      assertEquals(PRODUCTION, affectedModule.getType());
      assertEquals("intellij.database.dialects.sqlite", affectedModule.getName());
    }
  }

  private static File getTestDataFile(@NotNull String fileName) {
    return new File(PluginPathManager.getPluginHomePath("jps-cache") + "/testData/", fileName);
  }
}