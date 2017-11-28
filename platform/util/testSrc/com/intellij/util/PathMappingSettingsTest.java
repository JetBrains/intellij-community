package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author traff
 */
public class PathMappingSettingsTest {

  public static final String LOCAL_PATH_TO_FILE = "C:\\PythonSources\\src\\runner\\run.py";
  public static final String REMOTE_PATH_TO_FILE = "/home/testPrj/runner/run.py";

  private PathMappingSettings myMappingSettings;

  @Before
  public void setUp() {
    myMappingSettings = new PathMappingSettings();
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
  public void testConvertToLocalPathWithSeveralRemotePrefixInclusions() {
    myMappingSettings.addMapping("C:/testPrj/src", "/management");

    Assert.assertEquals("C:/testPrj/src/management/order.py", myMappingSettings.convertToLocal("/management/management/order.py"));
  }

  @Test
  public void testConvertToLocalPathWithPartialRemotePrefixFolderNameMatch() {
    myMappingSettings.addMapping("C:/testPrj/src", "/management");

    Assert.assertEquals("/management-data/users.db", myMappingSettings.convertToLocal("/management-data/users.db"));
  }
}
