package com.intellij.lang.properties;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.ParsingTestCase;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 29, 2005
 * Time: 9:04:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesParsingTest extends ParsingTestCase {
  public PropertiesParsingTest() {
    super("", "properties");
  }

  protected String testDataPath() {
    return PathManager.getHomePath() + "/plugins/Properties/testData";
  }

  public void testProp1() throws Exception {
    doTest(true);
  }
}
