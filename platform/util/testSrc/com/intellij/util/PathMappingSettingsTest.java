package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * @author traff
 */
public class PathMappingSettingsTest extends TestCase {

  public static final String LOCAL_PATH_TO_FILE = "C:\\PythonSources\\src\\runner\\run.py";
  public static final String REMOTE_PATH_TO_FILE = "/home/testPrj/runner/run.py";

  public void testTrailingSlashes() {
    PathMappingSettings mappingSettings = create();

    mappingSettings.addMapping("C:\\PythonSources\\src\\", "/home/testPrj");

    Assert.assertEquals("C:/PythonSources/src/runner/run.py", mappingSettings.convertToLocal(REMOTE_PATH_TO_FILE));
    Assert.assertEquals("/home/testPrj/runner/run.py", mappingSettings.convertToRemote(LOCAL_PATH_TO_FILE));
  }

  public void testCaseNormalizingOnWin() {
    PathMappingSettings mappingSettings = create();

    mappingSettings.addMapping("c:/pythonsources/src", "/home/testPrj/");

    if (SystemInfo.isWindows) {
      Assert.assertEquals(REMOTE_PATH_TO_FILE, mappingSettings.convertToRemote(LOCAL_PATH_TO_FILE));
    }
    else {
      Assert.assertEquals(LOCAL_PATH_TO_FILE, mappingSettings.convertToRemote(LOCAL_PATH_TO_FILE)); //don't convert
    }
  }

  public void testOverlappingPaths() {
    PathMappingSettings mappingSettings = create();
    mappingSettings.addMapping("V:/site-packages", "/usr/lib/python2.6/site-packages");
    mappingSettings.addMapping("V:/site-packages64", "/usr/lib64/python2.6/site-packages");
    mappingSettings.addMapping("V:/bfms/django_root", "/opt/bfms");
    mappingSettings.addMapping("V:/bfms", "/opt/bfms_trunk");

    Assert.assertEquals("/usr/lib64/python2.6/site-packages/django", mappingSettings.convertToRemote("V:\\site-packages64\\django"));
    Assert.assertEquals("V:/site-packages64/django", mappingSettings.convertToLocal("/usr/lib64/python2.6/site-packages/django"));
    Assert.assertEquals("/opt/bfms/myapp", mappingSettings.convertToRemote("V:/bfms/django_root/myapp"));
    Assert.assertEquals("V:/bfms/django_root/myapp", mappingSettings.convertToLocal("/opt/bfms/myapp"));
  }

  private static PathMappingSettings create() {
    return new PathMappingSettings();
  }
}
