package com.intellij.application;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformLangTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 1/9/13
 */
public class BadPluginTest extends PlatformLangTestCase {

  public void testBadPlugin() throws Exception {
    List<String> disabledPlugins = new ArrayList<String>();
    PluginManager.loadDisabledPlugins(PathManager.getConfigPath(), disabledPlugins);
    assertEquals(1, disabledPlugins.size());
    assertEquals("com.yourcompany.unique.plugin.id", disabledPlugins.get(0));
  }

  @Override
  protected void setUp() throws Exception {

    String path = PlatformTestUtil.getCommunityPath() + "/platform/platform-tests/testData/badPlugins";
    File directory = createTempDirectory(false);
    FileUtil.copyDir(new File(path), directory);

    System.setProperty(PathManager.PROPERTY_CONFIG_PATH, directory.getPath());
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    System.setProperty(PathManager.PROPERTY_CONFIG_PATH, myOldConfigPath);
    super.tearDown();
  }

  private String myOldConfigPath = System.getProperty(PathManager.PROPERTY_CONFIG_PATH);
}
