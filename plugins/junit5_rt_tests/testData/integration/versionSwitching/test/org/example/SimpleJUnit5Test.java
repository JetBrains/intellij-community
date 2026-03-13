package org.example;

import org.junit.jupiter.api.Test;
import java.security.CodeSource;
import java.net.URL;
import java.io.*;
import java.util.jar.*;

class SimpleJUnit5Test {

  @Test
  void testVersion() {
    System.out.println("junit6.classes.present=" + (getJUnitVersion() == 6));
  }

  private static int  getJUnitVersion() {
    try {
      CodeSource cs = Class.forName("org.junit.platform.engine.TestEngine").getProtectionDomain().getCodeSource();
      if (cs == null) return -1;
      URL manifestUrl = new URL("jar:" + cs.getLocation().toExternalForm() + "!/META-INF/MANIFEST.MF");
      try (InputStream is = manifestUrl.openStream()) {
        Manifest manifest = new Manifest(is);
        String version = manifest.getMainAttributes().getValue("Bundle-Version");
        if (version != null) {
          int dot = version.indexOf('.');
          return Integer.parseInt(dot >= 0 ? version.substring(0, dot) : version);
        }
      }
    }
    catch (Exception ignored) {
    }
    return -1;
  }
}
