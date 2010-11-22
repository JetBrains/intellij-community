package org.jetbrains.plugins.groovy.grape;

import groovy.lang.GroovyShell;

import java.net.URL;

/**
 * @author peter
 */
public class GrapeRunner {
  public static final String URL_PREFIX = "URL:";

  private GrapeRunner() {
  }

  public static void main(String[] args) {
    final GroovyShell shell = new GroovyShell();
    try {
      shell.parse(args[0] + " import java.lang.*");
    }
    catch (Throwable e) {
      e.printStackTrace();
      return;
    }

    URL[] urls = shell.getClassLoader().getURLs();
    for (int i = 0; i < urls.length; i++) {
      System.out.println(URL_PREFIX + urls[i]);
    }
  }

}
