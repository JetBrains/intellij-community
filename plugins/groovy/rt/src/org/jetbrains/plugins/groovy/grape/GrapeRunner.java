// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.grape;

import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.ExceptionMessage;

import java.net.URL;
import java.util.List;

/**
 * @author peter
 */
public final class GrapeRunner {
  public static final String URL_PREFIX = "URL:";

  private GrapeRunner() {
  }

  public static void main(String[] args) {
    final GroovyShell shell = new GroovyShell();
    try {
      shell.parse(args[0] + " import java.lang.*");
    }
    catch (MultipleCompilationErrorsException e) {
      List errors = e.getErrorCollector().getErrors();
      for (Object o : errors) {
        if (o instanceof ExceptionMessage) {
          Exception cause = ((ExceptionMessage)o).getCause();
          String message = cause.getMessage();
          if (message != null && message.startsWith("Error grabbing Grapes")) {
            System.out.println(message);
            return;
          }
        }
      }
      e.printStackTrace();
      return;
    }
    catch (Throwable e) {
      e.printStackTrace();
      return;
    }

    URL[] urls = shell.getClassLoader().getURLs();
    for (URL url : urls) {
      System.out.println(URL_PREFIX + url);
    }
  }

}
