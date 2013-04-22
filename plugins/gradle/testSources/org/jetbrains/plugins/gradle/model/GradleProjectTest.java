package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 8/29/11 1:28 PM
 */
public class GradleProjectTest {

  private ProjectData myProject;

  @Before
  public void setUp() {
    String path = new File(".").getPath();
    // TODO den implement
//    myProject = new ProjectData(path, path, id);
  }

  @Test
  public void jdkVersion() {
    doTestJdkVersion("4", JavaSdkVersion.JDK_1_4);
    doTestJdkVersion("   5", JavaSdkVersion.JDK_1_5);
    doTestJdkVersion("6   ", JavaSdkVersion.JDK_1_6);
    doTestJdkVersion("    7   ", JavaSdkVersion.JDK_1_7);
    doTestJdkVersion("    1.4   ", JavaSdkVersion.JDK_1_4);
    doTestJdkVersion("this is version 1.5.1_0b12   ", JavaSdkVersion.JDK_1_5);
  }

  private void doTestJdkVersion(@NotNull String version, @NotNull JavaSdkVersion expected) {
    // TODO den implement
//    myProject.setJdkVersion(version);
//    assertEquals(expected, myProject.getJdkVersion());
  }
}
