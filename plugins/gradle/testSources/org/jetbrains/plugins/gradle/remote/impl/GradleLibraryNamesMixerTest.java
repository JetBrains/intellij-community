package org.jetbrains.plugins.gradle.remote.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 10/19/11 5:25 PM
 */
public class GradleLibraryNamesMixerTest {
  
  private GradleLibraryNamesMixer myMixer;
  
  @Before
  public void setUp() {
    myMixer = new GradleLibraryNamesMixer();
  }
  
  @Test
  public void sourceVsTest() {
    doTest(
      t("resources", "my-module-resources", "dir1/dir2/my-module/src/main/resources"),
      t("resources", "my-module-test-resources", "dir1/dir2/my-module/src/test/resources"),
      t("resources", "my-another-module-resources", "dir1/dir2/my-another-module/src/main/resources"),
      t("resources", "my-another-module-test-resources", "dir1/dir2/my-another-module/src/test/resources")
    );
  }

  private void doTest(TestDataEntry... entries) {
    Map<GradleLibrary, String> expected = new IdentityHashMap<GradleLibrary, String>();
    List<GradleLibrary> libraries = new ArrayList<GradleLibrary>();
    for (TestDataEntry entry : entries) {
      GradleLibrary library = new GradleLibrary(entry.initialName);
      library.addPath(LibraryPathType.BINARY, entry.path);
      libraries.add(library);
      expected.put(library, entry.expectedName);
    }
    
    myMixer.mixNames(libraries);
    for (GradleLibrary library : libraries) {
      assertEquals(expected.get(library), library.getName());
    }
  }
  
  private static class TestDataEntry {
    
    public String initialName;
    public String expectedName;
    public String path;

    TestDataEntry(@NotNull String initialName, @NotNull String expectedName, @NotNull String path) {
      this.initialName = initialName;
      this.expectedName = expectedName;
      this.path = path;
    }
  }

  public static TestDataEntry t(@NotNull String initialName, @NotNull String expectedName, @NotNull String path) {
    return new TestDataEntry(initialName, expectedName, path);
  }
}
