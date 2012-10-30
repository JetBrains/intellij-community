package org.jetbrains.jps.appengine;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.appengine.model.JpsAppEngineExtensionService;
import org.jetbrains.jps.appengine.model.JpsAppEngineModuleExtension;
import org.jetbrains.jps.appengine.model.PersistenceApi;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsSerializationTestCase;

/**
 * @author nik
 */
public class JpsAppEngineSerializationTest extends JpsSerializationTestCase {
  public static final String PROJECT_PATH = "plugins/GoogleAppEngine/jps-plugin/testData/serialization/appEngine";

  public void testLoad() {
    loadProject(PROJECT_PATH + "/appEngine.ipr");
    JpsModule module = assertOneElement(myProject.getModules());
    assertEquals("appEngine", module.getName());
    JpsAppEngineModuleExtension extension = JpsAppEngineExtensionService.getInstance().getExtension(module);
    assertNotNull(extension);
    assertEquals(PersistenceApi.JPA2, extension.getPersistenceApi());
    assertEquals(FileUtil.toSystemIndependentName(getTestDataFileAbsolutePath(PROJECT_PATH) + "/src"), assertOneElement(extension.getFilesToEnhance()));
    assertTrue(extension.isRunEnhancerOnMake());
  }
}
