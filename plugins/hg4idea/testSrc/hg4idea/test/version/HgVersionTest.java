/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package hg4idea.test.version;

import com.intellij.openapi.vcs.VcsTestUtil;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.util.HgVersion;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Nadya Zabrodina
 */
public class HgVersionTest extends HgPlatformTest {

  //todo: should be changed to Junit Parameterized tests
  private static final TestHgVersion[] commonTests = {
    new TestHgVersion("Mercurial Distributed SCM (version 2.6.2)", 2, 6, 2),
    new TestHgVersion("Mercurial Distributed SCM (version 2.6+20130507)", 2, 6, 0),
    new TestHgVersion("Mercurial Distributed SCM (version 2.0.1)", 2, 0, 1),
    new TestHgVersion("Mercurial Distributed SCM (version 2.6)", 2, 6, 0),
    new TestHgVersion("Mercurial Distributed SCM (version 2.7-rc+5-ca2dfc2f63eb)", 2, 7, 0),
    new TestHgVersion("Распределенная SCM Mercurial (версия 2.0.2)", 2, 0, 2),
    new TestHgVersion("Mercurial Distributed SCM (version 2.4.2+20130102)", 2, 4, 2),
    new TestHgVersion("Распределенная SCM Mercurial (версия 2.6.1)", 2, 6, 1),
    new TestHgVersion("[Mercurial Distributed SCM (version 2.6.2+20130606)]", 2, 6, 2),
    new TestHgVersion("[Mercurial Distributed SCM (version 2.4.2+20130203)]\n", 2, 4, 2),
    new TestHgVersion("Mercurial Distributed SCM (version 2.6.2)\n", 2, 6, 2),
    new TestHgVersion("Mercurial Distributed SCM (version 2.7+93-f959b60e3025)", 2, 7, 0)
  };

  public void testParseSupported() throws Exception {
    for (TestHgVersion test : commonTests) {
      HgVersion version = HgVersion.parseVersionAndExtensionInfo(test.output, Collections.<String>emptyList());
      assertEqualVersions(version, test);
      assertTrue(version.isSupported());
    }
  }

  public void testParseUnsupported() throws Exception {
    TestHgVersion unsupportedVersion = new TestHgVersion("Mercurial Distributed SCM (version 1.5.1)", 1, 5, 1);
    HgVersion parsedVersion =
      HgVersion.parseVersionAndExtensionInfo(unsupportedVersion.output, Collections.<String>emptyList());
    assertEqualVersions(parsedVersion, unsupportedVersion);
    assertFalse(parsedVersion.isSupported());
  }

  public void testParseImportExtensionsError() {
    List<String> errorLines = Arrays.asList("*** failed to import extension hgcr-gui: No module named hgcr-gui",
                                            "*** failed to import extension hgcr-gui-qt: No module named hgcr-gui-qt");
    VcsTestUtil.assertEqualCollections(HgVersion.parseUnsupportedExtensions(errorLines), Arrays.asList("hgcr-gui", "hgcr-gui-qt"));
  }

  public void testParseImportDeprecatedExtensionsError() {
    List<String> errorLines = Arrays.asList("*** failed to import extension kilnpath from" +
                                            " C:\\Users\\Developer\\AppData\\Local\\KilnExtensions\\kilnpath.py:" +
                                            " kilnpath is deprecated, and does not work in Mercurial 2.3 or higher." +
                                            "  Use the official schemes extension instead");
    VcsTestUtil.assertEqualCollections(HgVersion.parseUnsupportedExtensions(errorLines), Arrays.asList("kilnpath"));
  }

  private static void assertEqualVersions(HgVersion actual, TestHgVersion expected) throws Exception {
    Field field = HgVersion.class.getDeclaredField("myMajor");
    field.setAccessible(true);
    final int major = field.getInt(actual);
    field = HgVersion.class.getDeclaredField("myMiddle");
    field.setAccessible(true);
    final int middle = field.getInt(actual);
    field = HgVersion.class.getDeclaredField("myMinor");
    field.setAccessible(true);
    final int minor = field.getInt(actual);

    assertEquals(major, expected.major);
    assertEquals(middle, expected.middle);
    assertEquals(minor, expected.minor);
    HgVersion versionFromTest = new HgVersion(expected.major, expected.middle, expected.minor);
    assertEquals(versionFromTest, actual); //test equals method
  }

  private static class TestHgVersion {
    private final String output;
    private final int major;
    private final int middle;
    private final int minor;

    public TestHgVersion(String output, int major, int middle, int minor) {
      this.output = output;
      this.major = major;
      this.middle = middle;
      this.minor = minor;
    }
  }
}
