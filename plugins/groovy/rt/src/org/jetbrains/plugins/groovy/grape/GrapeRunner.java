package org.jetbrains.plugins.groovy.grape;

import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilationFailedException;

import java.io.File;
import java.net.URL;

/**
 * @author peter
 */
public class GrapeRunner {
  public static final String URL_PREFIX = "URL:";

  private GrapeRunner() {
  }

  public static void main(String[] args) {
    final File file = new File(args[0]);
    if (!file.exists()) {
      return;
    }

    final GroovyShell shell = new GroovyShell();
    try {
      shell.parse(file);
    }
    catch (CompilationFailedException ignored) {
      //should fail, we're not compiling, we're just resolving Grab dependencies
    }
    catch (Throwable e) {
      e.printStackTrace();
    }

    URL[] URLs = shell.getClassLoader().getURLs();
    for (int i = 0, URLsLength = URLs.length; i < URLsLength; i++) {
      System.out.println(URL_PREFIX + URLs[i]);
    }
  }

}
