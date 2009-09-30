/*
 * @author max
 */
package com.intellij.openapi.util;

import junit.framework.TestCase;

public class BuildNumberTest extends TestCase {
  public void testHistoricBuild() {
    assertEquals(new BuildNumber("", 75, 7512), BuildNumber.fromString("7512"));
  }
  
  public void testSnapshotDominates() {
    assertTrue(BuildNumber.fromString("90.SNAPSHOT").compareTo(BuildNumber.fromString("90.12345")) > 0);
    assertTrue(BuildNumber.fromString("IU-90.SNAPSHOT").compareTo(BuildNumber.fromString("RM-90.12345")) > 0);
  }
}
