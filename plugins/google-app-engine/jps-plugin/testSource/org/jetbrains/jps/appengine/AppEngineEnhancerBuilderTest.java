package org.jetbrains.jps.appengine;

import com.google.appengine.tools.enhancer.Enhance;
import com.intellij.openapi.application.PathManager;
import com.intellij.util.PathUtil;
import org.jetbrains.jps.appengine.build.AppEngineEnhancerBuilder;
import org.jetbrains.jps.appengine.model.JpsAppEngineModuleExtension;
import org.jetbrains.jps.appengine.model.impl.AppEngineModuleExtensionProperties;
import org.jetbrains.jps.appengine.model.impl.JpsAppEngineModuleExtensionImpl;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;

/**
 * @author nik
 */
public class AppEngineEnhancerBuilderTest extends JpsBuildTestCase {
  public void testChangeFile() {
    String file = createFile("src/A.java", "class A{}");
    addAppEngineModule("a", true, PathUtil.getParentPath(file));
    makeAll();
    assertEnhanced("out/production/a/A.class");

    makeAll();
    assertEnhanced();

    change(file);
    makeAll();
    assertEnhanced("out/production/a/A.class");
  }

  public void testDoNotRunEnhancerIfDisabled() {
    String file = createFile("src/A.java", "class A{}");
    addAppEngineModule("a", false, PathUtil.getParentPath(file));
    makeAll();
    assertEnhanced();
  }

  private void assertEnhanced(final String... paths) {
    assertCompiled(AppEngineEnhancerBuilder.NAME, paths);
  }

  private void addAppEngineModule(final String moduleName, final boolean runEnhancerOnMake, String srcRoot) {
    JpsModule module = addModule(moduleName, srcRoot);
    AppEngineModuleExtensionProperties properties = new AppEngineModuleExtensionProperties();
    properties.myRunEnhancerOnMake = runEnhancerOnMake;
    properties.myFilesToEnhance.add(srcRoot);
    JpsAppEngineModuleExtension extension = new JpsAppEngineModuleExtensionImpl(properties);
    module.getContainer().setChild(JpsAppEngineModuleExtensionImpl.ROLE, extension);
    addEnhancerLibrary(module);
  }

  private static void addEnhancerLibrary(JpsModule module) {
    String path = PathManager.getJarPathForClass(Enhance.class);
    JpsLibrary library = module.addModuleLibrary("appengine-enhancer", JpsJavaLibraryType.INSTANCE);
    library.addRoot(new File(path), JpsOrderRootType.COMPILED);
    module.getDependenciesList().addLibraryDependency(library);
  }
}
