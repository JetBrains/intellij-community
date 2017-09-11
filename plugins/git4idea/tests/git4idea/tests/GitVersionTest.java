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
package git4idea.tests;

import com.intellij.openapi.util.SystemInfo;
import git4idea.config.GitVersion;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

import static git4idea.config.GitVersion.Type;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Kirill Likhodedov
 */
public class GitVersionTest {

  private static final TestGitVersion[] commonTests = {
    new TestGitVersion("git version 1.6.4", 1, 6, 4, 0),
    new TestGitVersion("git version 1.7.3.3", 1, 7, 3, 3),
    new TestGitVersion("git version 1.7.3.5.gb27be", 1, 7, 3, 5),
    new TestGitVersion("git version 1.7.4-rc1", 1, 7, 4, 0),
    new TestGitVersion("git version 1.7.7.5 (Apple Git-24)", 1, 7, 7, 5),
    new TestGitVersion("git version 1.9.rc0.143.g6fd479e", 1, 9, 0, 0)
  };

  private static final TestGitVersion[] msysTests = {
    new TestGitVersion("git version 1.6.4.msysgit.0", 1, 6, 4, 0),
    new TestGitVersion("git version 1.7.3.3.msysgit.1", 1, 7, 3, 3),
    new TestGitVersion("git version 1.7.3.2.msysgit", 1, 7, 3, 2),
    new TestGitVersion("git version 1.7.3.5.msysgit.gb27be", 1, 7, 3, 5)
  };

  /**
   * Tests that parsing the 'git version' command output returns the correct GitVersion object.
   * Tests on UNIX and on Windows - both CYGWIN and MSYS.
   */
  @Test
  public void testParse() throws Exception {
    if (SystemInfo.isUnix) {
      for (TestGitVersion test : commonTests) {
        GitVersion version = GitVersion.parse(test.output);
        assertEqualVersions(version, test, Type.UNIX);
      }
    }
    else if (SystemInfo.isWindows) {
      for (TestGitVersion test : commonTests) {
        GitVersion version = GitVersion.parse(test.output);
        assertEqualVersions(version, test, Type.CYGWIN);
      }

      for (TestGitVersion test : msysTests) {
        GitVersion version = GitVersion.parse(test.output);
        assertEqualVersions(version, test, Type.MSYS);
      }
    }
  }

  @Test
  public void testEqualsAndCompare() throws Exception {
    GitVersion v1 = GitVersion.parse("git version 1.6.3");
    GitVersion v2 = GitVersion.parse("git version 1.7.3");
    GitVersion v3 = GitVersion.parse("git version 1.7.3");
    GitVersion v4 = GitVersion.parse("git version 1.7.3.msysgit.0");
    assertFalse(v1.equals(v2));
    assertFalse(v1.equals(v3));
    Assert.assertTrue(v2.equals(v3));
    if (SystemInfo.isWindows) {
      assertFalse(v1.equals(v4));
      assertFalse(v2.equals(v4));
      assertFalse(v3.equals(v4));
    }

    assertEquals(-1, v1.compareTo(v2));
    assertEquals(-1, v1.compareTo(v3));
    assertEquals(-1, v1.compareTo(v4));
    assertEquals(0, v2.compareTo(v3));
    assertEquals(0, v2.compareTo(v4));
    assertEquals(0, v3.compareTo(v4));
  }

  // Compares the parsed output and what we've expected.
  // Uses reflection to get private fields of GitVersion: we don't need them in code, so no need to trash the class with unused accessors.
  private static void assertEqualVersions(GitVersion actual, TestGitVersion expected, Type expectedType) throws Exception {
    Field field = GitVersion.class.getDeclaredField("myMajor");
    field.setAccessible(true);
    final int major = field.getInt(actual);
    field = GitVersion.class.getDeclaredField("myMinor");
    field.setAccessible(true);
    final int minor = field.getInt(actual);
    field = GitVersion.class.getDeclaredField("myRevision");
    field.setAccessible(true);
    final int rev = field.getInt(actual);
    field = GitVersion.class.getDeclaredField("myPatchLevel");
    field.setAccessible(true);
    final int patch = field.getInt(actual);
    field = GitVersion.class.getDeclaredField("myType");
    field.setAccessible(true);
    final Type type = (Type) field.get(actual);

    assertEquals(expected.major, major);
    assertEquals(expected.minor, minor);
    assertEquals(expected.rev, rev);
    assertEquals(expected.patch, patch);
    assertEquals(expectedType, type);
  }

  private static class TestGitVersion {
    private final String output;
    private final int major;
    private final int minor;
    private final int rev;
    private final int patch;

    public TestGitVersion(String output, int major, int minor, int rev, int patch) {
      this.output = output;
      this.major = major;
      this.minor = minor;
      this.rev = rev;
      this.patch = patch;
    }
  }

}
