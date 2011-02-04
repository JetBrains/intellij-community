/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 19, 2002
 * Time: 3:27:12 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.util;

import com.intellij.util.EnvironmentUtil;
import com.intellij.openapi.util.SystemInfo;
import junit.framework.TestCase;

import java.util.Map;

public class EnvironmentUtilTest extends TestCase {
  public void test1() {
    Map enviromentProperties = EnvironmentUtil.getEnviromentProperties();
    assertNotNull(enviromentProperties);
    if(SystemInfo.isWindows)
      assertNotNull(enviromentProperties.get("Path"));
    else
      assertNotNull(enviromentProperties.get("PATH"));
  }
}
