package org.example;

import org.junit.jupiter.api.Test;

class SimpleJUnit5Test {

  @Test
  void testVersion() {
    System.out.println("junit6.classes.present=" + isPresent("org.junit.platform.engine.CancellationToken"));
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
