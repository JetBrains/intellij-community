package com.intellij.jps.cache;

import com.intellij.openapi.application.PathManager;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.impl.JpsSimpleElementImpl;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import static com.intellij.jps.cache.JpsCachesPluginUtil.COMPATIBLE_JPS_VERSION;

public class JpsCompatibilityCheckerTest extends BasePlatformTestCase {
  public void testJpsVersionInPluginSameAsActual() {
    JpsProject ultimateProject = IntelliJProjectConfiguration.loadIntelliJProject(PathManager.getHomePath());
    JpsLibrary jpsLibrary = ultimateProject.getLibraryCollection().getLibraries().stream()
      .filter(library -> library.getName().equals("jps-build-script-dependencies-bootstrap")).findFirst().orElseThrow();

    String jpsLibraryVersion = ((JpsSimpleElementImpl<JpsMavenRepositoryLibraryDescriptor>)jpsLibrary.getProperties()).getData().getVersion();
    assertEquals("Please don't forget to update JPS version value at plugin too", COMPATIBLE_JPS_VERSION, jpsLibraryVersion);
  }
}
