package org.example;

import junit.framework.TestCase;

public class SimpleJUnit3Test extends TestCase {

  public void testVersion() {
    System.out.println("junit4.classes.present=" + isPresent("org.junit.Test"));
  }

  public void testAnother() {
    assertTrue(true);
  }

  private static boolean isPresent(String className) {
    try {
      Class.forName(className);
      return true;
    }
    catch (ClassNotFoundException e) {
      return false;
    }
  }
}
