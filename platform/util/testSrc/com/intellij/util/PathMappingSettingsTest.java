// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PathMappingSettingsTest {

  public static final String LOCAL_PATH_TO_FILE = "C:\\PythonSources\\src\\runner\\run.py";
  public static final String REMOTE_PATH_TO_FILE = "/home/testPrj/runner/run.py";

  private PathMappingSettings myMappingSettings;

  @BeforeEach
  public void setUp() {
    myMappingSettings = new PathMappingSettings();
  }

  @Test
  public void testNoSlashBetweenParts() {
    myMappingSettings.addMapping("//wsl$/Debian", "/");
    Assertions.assertEquals("//wsl$/Debian/home/link/my.py", myMappingSettings.convertToLocal("/home/link/my.py"));
  }

  @Test
  public void testTrailingSlashes() {
    myMappingSettings.addMapping("C:\\PythonSources\\src\\", "/home/testPrj");

    Assertions.assertEquals("C:/PythonSources/src/runner/run.py", myMappingSettings.convertToLocal(REMOTE_PATH_TO_FILE));
    Assertions.assertEquals("/home/testPrj/runner/run.py", myMappingSettings.convertToRemote(LOCAL_PATH_TO_FILE));
  }

  @Test
  public void testCaseNormalizingOnWin() {
    myMappingSettings.addMapping("c:/pythonsources/src", "/home/testPrj/");

    if (SystemInfo.isWindows) {
      Assertions.assertEquals(REMOTE_PATH_TO_FILE, myMappingSettings.convertToRemote(LOCAL_PATH_TO_FILE));
    }
    else {
      Assertions.assertEquals(LOCAL_PATH_TO_FILE, myMappingSettings.convertToRemote(LOCAL_PATH_TO_FILE)); //don't convert
    }
  }

  @Test
  public void testOverlappingPaths() {
    myMappingSettings.addMapping("V:/site-packages", "/usr/lib/python2.6/site-packages");
    myMappingSettings.addMapping("V:/site-packages64", "/usr/lib64/python2.6/site-packages");
    myMappingSettings.addMapping("V:/bfms/django_root", "/opt/bfms");
    myMappingSettings.addMapping("V:/bfms", "/opt/bfms_trunk");

    Assertions.assertEquals("/usr/lib64/python2.6/site-packages/django", myMappingSettings.convertToRemote("V:\\site-packages64\\django"));
    Assertions.assertEquals("V:/site-packages64/django", myMappingSettings.convertToLocal("/usr/lib64/python2.6/site-packages/django"));
    Assertions.assertEquals("/opt/bfms/myapp", myMappingSettings.convertToRemote("V:/bfms/django_root/myapp"));
    Assertions.assertEquals("V:/bfms/django_root/myapp", myMappingSettings.convertToLocal("/opt/bfms/myapp"));
  }
  @Test
  public void testRootDriveMapping() {
    Assumptions.assumeTrue(SystemInfoRt.isWindows, "Windows only test");
    myMappingSettings.addMapping("C:\\", "/mnt/c");
    assertEquals(Path.of("Windows"),
                 Path.of(myMappingSettings.convertToLocal("/mnt/c")).relativize(Path.of("c:\\Windows")),
                 "Incorrect mapping");
  }
  @Test
  public void testConvertToLocalPathWithSeveralRemotePrefixInclusions() {
    myMappingSettings.addMapping("C:/testPrj/src", "/management");

    Assertions.assertEquals("C:/testPrj/src/management/order.py", myMappingSettings.convertToLocal("/management/management/order.py"));
  }

  @Test
  public void testConvertToLocalPathWithPartialRemotePrefixFolderNameMatch() {
    myMappingSettings.addMapping("C:/testPrj/src", "/management");

    Assertions.assertEquals("/management-data/users.db", myMappingSettings.convertToLocal("/management-data/users.db"));
  }

  @Test
  public void testConvertToRemoteWithFileWithTheSamePrefix() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src", "/web-project");

    Assertions.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assertions.assertEquals("/web-project", myMappingSettings.convertToRemote("C:/testPrj/src"));
    Assertions.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));
    Assertions.assertEquals("/web-project/", myMappingSettings.convertToRemote("C:/testPrj/src/"));

    Assertions.assertEquals("/web-project/tests.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests.php"));
    Assertions.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithFileWithTheSamePrefixWithTrailingSlashInLocal() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src/", "/web-project");

    Assertions.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assertions.assertEquals("/web-project", myMappingSettings.convertToRemote("C:/testPrj/src"));
    Assertions.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));
    Assertions.assertEquals("/web-project/", myMappingSettings.convertToRemote("C:/testPrj/src/"));

    Assertions.assertEquals("/web-project/tests.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests.php"));
    Assertions.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithFileWithTheSamePrefixWithTrailingSlashInRemote() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src", "/web-project/");

    Assertions.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assertions.assertEquals("/web-project", myMappingSettings.convertToRemote("C:/testPrj/src"));
    Assertions.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));
    Assertions.assertEquals("/web-project/", myMappingSettings.convertToRemote("C:/testPrj/src/"));

    Assertions.assertEquals("/web-project/tests.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests.php"));
    Assertions.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithFileWithTheSamePrefixWithTrailingSlash() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src/", "/web-project/");

    Assertions.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assertions.assertEquals("/web-project", myMappingSettings.convertToRemote("C:/testPrj/src"));
    Assertions.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));
    Assertions.assertEquals("/web-project/", myMappingSettings.convertToRemote("C:/testPrj/src/"));

    Assertions.assertEquals("/web-project/tests.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests.php"));
    Assertions.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithDirectorWithTheSamePrefix() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src/tests", "/web-project/bar");

    Assertions.assertEquals("/web-project/bar", myMappingSettings.convertToRemote("C:/testPrj/src/tests"));
    Assertions.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assertions.assertEquals("/web-project/bar/", myMappingSettings.convertToRemote("C:/testPrj/src/tests/"));
    Assertions.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));

    Assertions.assertEquals("/web-project/bar/my-test.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests/my-test.php"));
    Assertions.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithDirectorWithTheSamePrefixWithTrailingSlashInLocal() {
    myMappingSettings.addMapping("C:/testPrj/src/test/", "/web-project/foo");
    myMappingSettings.addMapping("C:/testPrj/src/tests/", "/web-project/bar");

    Assertions.assertEquals("/web-project/bar", myMappingSettings.convertToRemote("C:/testPrj/src/tests"));
    Assertions.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assertions.assertEquals("/web-project/bar/", myMappingSettings.convertToRemote("C:/testPrj/src/tests/"));
    Assertions.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));

    Assertions.assertEquals("/web-project/bar/my-test.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests/my-test.php"));
    Assertions.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithDirectorWithTheSamePrefixWithTrailingSlashInRemote() {
    myMappingSettings.addMapping("C:/testPrj/src/test", "/web-project/foo/");
    myMappingSettings.addMapping("C:/testPrj/src/tests", "/web-project/bar/");

    Assertions.assertEquals("/web-project/bar", myMappingSettings.convertToRemote("C:/testPrj/src/tests"));
    Assertions.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assertions.assertEquals("/web-project/bar/", myMappingSettings.convertToRemote("C:/testPrj/src/tests/"));
    Assertions.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));

    Assertions.assertEquals("/web-project/bar/my-test.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests/my-test.php"));
    Assertions.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testConvertToRemoteWithDirectorWithTheSamePrefixWithTrailingSlash() {
    myMappingSettings.addMapping("C:/testPrj/src/test/", "/web-project/foo/");
    myMappingSettings.addMapping("C:/testPrj/src/tests/", "/web-project/bar/");

    Assertions.assertEquals("/web-project/bar", myMappingSettings.convertToRemote("C:/testPrj/src/tests"));
    Assertions.assertEquals("/web-project/foo", myMappingSettings.convertToRemote("C:/testPrj/src/test"));
    Assertions.assertEquals("/web-project/bar/", myMappingSettings.convertToRemote("C:/testPrj/src/tests/"));
    Assertions.assertEquals("/web-project/foo/", myMappingSettings.convertToRemote("C:/testPrj/src/test/"));

    Assertions.assertEquals("/web-project/bar/my-test.php", myMappingSettings.convertToRemote("C:/testPrj/src/tests/my-test.php"));
    Assertions.assertEquals("/web-project/foo/info.php", myMappingSettings.convertToRemote("C:/testPrj/src/test/info.php"));
  }

  @Test
  public void testAddMappingCheckUnique() {
    myMappingSettings.addMappingCheckUnique("C:/testPrj/src/test/", "/web-project/");
    myMappingSettings.addMappingCheckUnique("C:/testPrj/src/test", "/web-project/");
    myMappingSettings.addMappingCheckUnique("C:/testPrj/src/test/", "/web-project");
    myMappingSettings.addMappingCheckUnique("C:/testPrj/src/test", "/web-project");

    Assertions.assertEquals(Collections.singletonList(new PathMappingSettings.PathMapping("C:/testPrj/src/test", "/web-project")),
                            myMappingSettings.getPathMappings());
  }
}
