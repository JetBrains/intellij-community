// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;

public class DumpTestEnvironmentTest {
  @Test
  public void dumpEnvironment() throws Exception {
    Properties properties = System.getProperties();
    for (String propertyName : properties.keySet().stream().map(String.class::cast).sorted().collect(Collectors.toList())) {
      System.out.println("PROPERTY " + propertyName + " = " +
                         (isSecretParameter(propertyName) ? "[REDACTED]" : properties.getProperty(propertyName)));

    }

    for (String key : System.getenv().keySet().stream().sorted().collect(Collectors.toList())) {
      System.out.println("ENV " + key + " = " +
                         (isSecretParameter(key) ? "[REDACTED]" : System.getenv(key)));
    }

    System.out.println("*** Classloaders NOT SORTED CLASSPATH ***");
    dumpClassloader(getClass().getClassLoader(), false, 0);
    System.out.println("*** END NOT SORTED CLASSPATH ***");

    System.out.println();

    System.out.println("*** Classloaders SORTED CLASSPATH ***");
    dumpClassloader(getClass().getClassLoader(), true, 0);
    System.out.println("*** END SORTED CLASSPATH ***");
  }

  private static void dumpClassloader(ClassLoader loader, boolean sorted, int indent) throws Exception {
    System.out.print(StringUtil.repeatSymbol(' ', indent));
    System.out.print("CLASSLOADER ");
    System.out.println(loader.getClass().getName());

    Method getUrlsMethod = ReflectionUtil.getMethod(loader.getClass(), "getUrls");

    List<String> urls = new ArrayList<>();
    if (loader instanceof URLClassLoader) {
      urls.addAll(ContainerUtil.map(((URLClassLoader)loader).getURLs(), url -> url.toString()));
    } else if (getUrlsMethod != null) {
      //noinspection unchecked
      urls.addAll(ContainerUtil.map(((Collection<URL>)getUrlsMethod.invoke(loader)), url -> url.toString()));
    }

    if (sorted) {
      Collections.sort(urls);
    }

    for (String url : urls) {
      System.out.print(StringUtil.repeatSymbol(' ', indent));
      System.out.println(" - " + url);
    }

    if (loader.getParent() != null) {
      dumpClassloader(loader.getParent(), sorted, indent + 1);
    }
  }

  @Test
  public void testSecretParameters() {
    Assert.assertTrue(isSecretParameter("bla-token"));
    Assert.assertTrue(isSecretParameter("bla.secret.param"));
    Assert.assertTrue(isSecretParameter("idea.pasSword"));
    Assert.assertTrue(isSecretParameter("npm.auth.KEY"));
  }

  private static boolean isSecretParameter(String name) {
    for (String word : name.split("\\W+")) {
      if (word.equalsIgnoreCase("secret") ||
          word.equalsIgnoreCase("token") ||
          word.equalsIgnoreCase("key") ||
          word.equalsIgnoreCase("password")) {
        return true;
      }
    }

    return false;
  }
}
