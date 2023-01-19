// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class PathMappingSettingsTest {

  public static final String LOCAL_PATH_TO_FILE = "C:\\PythonSources\\src\\runner\\run.py";
  public static final String REMOTE_PATH_TO_FILE = "/home/testPrj/runner/run.py";

  private PathMappingSettings myMappingSettings;

  @Before
  public void setUp() {
    myMappingSettings = new PathMappingSettings();
  }

  @Test
  public void testNoSlashBetweenParts() {
    myMappingSettings.addMapping("//wsl$/Debian", "/");
    Assert.assertEquals("//wsl$/Debian/home/link/my.py", myMappingSettings.convertToLocal("/home/link/my.py"));
  }

  @Test
  public void testTrailingSlashes() {
    myMappingSettings.addMapping("C:\\PythonSources\\src\\", "/home/testPrj");

    Assert.assertEquals("C:/PythonSources/src/runner/run.py", myMappingSettings.convertToLocal(REMOTE_PATH_TO_FILE));
    Assert.assertEquals("/home/testPrj/runner/run.py", myMappingSettings.convertToRemote(LOCAL_PATH_TO_FILE));
  }

  @Test
  public void testCaseNormalizingOnWin() {
    myMappingSettings.addMapping("c:/pythonsources/src", "/home/testPrj/");

    if (SystemInfo.isWindows) {
      Assert.assertEquals(REMOTE_PATH_TO_FILE, myMappingSettings.convertToRemote(LOCAL_PATH_TO_FILE));
    }
    else {
      Assert.assertEquals(LOCAL_PATH_TO_FILE, myMappingSettings.convertToRemote(LOCAL_PATH_TO_FILE)); //don't convert
    }
  }

  @Test
  public void testOverlappingPaths() {
    myMappingSettings.addMapping("V:/site-packages", "/usr/lib/python2.6/site-packages");
    myMappingSettings.addMapping("V:/site-packages64", "/usr/lib64/python2.6/site-packages");
    myMappingSettings.addMapping("V:/bfms/django_root", "/opt/bfms");
    myMappingSettings.addMapping("V:/bfms", "/opt/bfms_trunk");

    Assert.assertEquals("/usr/lib64/python2.6/site-packages/django", myMappingSettings.convertToRemote("V:\\site-packages64\\django"));
    Assert.assertEquals("V:/site-packages64/django", myMappingSettings.convertToLocal("/usr/lib64/python2.6/site-packages/django"));
    Assert.assertEquals("/opt/bfms/myapp", myMappingSettings.convertToRemote("V:/bfms/django_root/myapp"));
    Assert.assertEquals("V:/bfms/django_root/myapp", myMappingSettings.convertToLocal("/opt/bfms/myapp"));
  }
  @Test
  public void testRootDriveMapping() {
    Assume.assumeTrue("Windows only test", SystemInfoRt.isWindows);
    myMappingSettings.addMapping("C:\\", "/mnt/c");
    assertEquals("Incorrect mapping",
                 Path.of("Windows"),
                 Path.of(myMappingSettings.convertToLocal("/mnt/c")).relativize(Path.of("c:\\Windows")));
  }
  @Test
  public void testConvertToLocalPathWithSeveralRemotePrefixInclusions() {
    myMappingSettings.addMapping("C:/testPrj/src", "/management");

    Assert.assertEquals("C:/testPrj/src/management/order.py", myMappingSettings.convertToLocal("/management/management/order.py"));
  }

  @Test
  public void testConvertToLocalPathWithPartialRemotePrefixFolderNameMatch() {
    myMappingSettings.addMapping("C:/testPrj/src", "/management");

    Assert.assertEquals("/management-data/users.db", myMappingSettings.convertToLocal("/management-data/users.db"));
  }

  @Test
  public void testConvertToRemoteWithFileWithTheSamePrefix() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src", "/web-project");

    Assert.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assert.assertEquals("/web-project", myMappingSettings.convertToRemote("C:/testPrj/src"));
    Assert.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));
    Assert.assertEquals("/web-project/", myMappingSettings.convertToRemote("C:/testPrj/src/"));

    Assert.assertEquals("/web-project/tests.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests.php"));
    Assert.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithFileWithTheSamePrefixWithTrailingSlashInLocal() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src/", "/web-project");

    Assert.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assert.assertEquals("/web-project", myMappingSettings.convertToRemote("C:/testPrj/src"));
    Assert.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));
    Assert.assertEquals("/web-project/", myMappingSettings.convertToRemote("C:/testPrj/src/"));

    Assert.assertEquals("/web-project/tests.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests.php"));
    Assert.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithFileWithTheSamePrefixWithTrailingSlashInRemote() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src", "/web-project/");

    Assert.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assert.assertEquals("/web-project", myMappingSettings.convertToRemote("C:/testPrj/src"));
    Assert.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));
    Assert.assertEquals("/web-project/", myMappingSettings.convertToRemote("C:/testPrj/src/"));

    Assert.assertEquals("/web-project/tests.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests.php"));
    Assert.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithFileWithTheSamePrefixWithTrailingSlash() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src/", "/web-project/");

    Assert.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assert.assertEquals("/web-project", myMappingSettings.convertToRemote("C:/testPrj/src"));
    Assert.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));
    Assert.assertEquals("/web-project/", myMappingSettings.convertToRemote("C:/testPrj/src/"));

    Assert.assertEquals("/web-project/tests.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests.php"));
    Assert.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithDirectorWithTheSamePrefix() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src/tests", "/web-project/bar");

    Assert.assertEquals("/web-project/bar", myMappingSettings.convertToRemote("C:/testPrj/src/tests"));
    Assert.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assert.assertEquals("/web-project/bar/", myMappingSettings.convertToRemote("C:/testPrj/src/tests/"));
    Assert.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));

    Assert.assertEquals("/web-project/bar/my-test.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests/my-test.php"));
    Assert.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithDirectorWithTheSamePrefixWithTrailingSlashInLocal() {
    myMappingSettings.addMapping("C:/testPrj/src/test/", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src/tests/", "/web-project/bar");

    Assert.assertEquals("/web-project/bar", myMappingSettings.convertToRemote("C:/testPrj/src/tests"));
    Assert.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assert.assertEquals("/web-project/bar/", myMappingSettings.convertToRemote("C:/testPrj/src/tests/"));
    Assert.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));

    Assert.assertEquals("/web-project/bar/my-test.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests/my-test.php"));
    Assert.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithDirectorWithTheSamePrefixWithTrailingSlashInRemote() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo/");
    myMappingSettings.addMapping("C:/testPrj/src/tests", "/web-project/bar/");

    Assert.assertEquals("/web-project/bar", myMappingSettings.convertToRemote("C:/testPrj/src/tests"));
    Assert.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assert.assertEquals("/web-project/bar/", myMappingSettings.convertToRemote("C:/testPrj/src/tests/"));
    Assert.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));

    Assert.assertEquals("/web-project/bar/my-test.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests/my-test.php"));
    Assert.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithDirectorWithTheSamePrefixWithTrailingSlash() {
    myMappingSettings.addMapping("C:/testPrj/src/test/", "/web-project/foo/");
    myMappingSettings.addMapping("C:/testPrj/src/tests/", "/web-project/bar/");

    Assert.assertEquals("/web-project/bar", myMappingSettings.convertToRemote("C:/testPrj/src/tests"));
    Assert.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assert.assertEquals("/web-project/bar/", myMappingSettings.convertToRemote("C:/testPrj/src/tests/"));
    Assert.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));

    Assert.assertEquals("/web-project/bar/my-test.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests/my-test.php"));
    Assert.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testAddMappingCheckUnique() {
    myMappingSettings.addMappingCheckUnique("C:/testPrj/src/test/", "/web-project/");
    myMappingSettings.addMappingCheckUnique("C:/testPrj/src/test", "/web-project/");
    myMappingSettings.addMappingCheckUnique("C:/testPrj/src/test/", "/web-project");
    myMappingSettings.addMappingCheckUnique("C:/testPrj/src/test", "/web-project");

    Assert.assertEquals(Collections.singletonList(new PathMappingSettings.PathMapping("C:/testPrj/src/test", "/web-project")),
                        myMappingSettings.getPathMappings());
  }
}
