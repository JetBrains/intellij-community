package com.intellij.appengine.sdk.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashMap;
import gnu.trove.THashSet;

import java.io.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class AppEngineSdkUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.sdk.impl.AppEngineSdkUtil");

  private AppEngineSdkUtil() {
  }

  public static void saveWhiteList(File cachedWhiteList, Map<String, Set<String>> classesWhiteList) {
    try {
      cachedWhiteList.getParentFile().mkdirs();
      PrintWriter writer = new PrintWriter(cachedWhiteList);
      for (String packageName : classesWhiteList.keySet()) {
        writer.println("." + packageName);
        final Set<String> classes = classesWhiteList.get(packageName);
        for (String aClass : classes) {
          writer.println(aClass);
        }
      }
      writer.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static Map<String, Set<String>> loadWhiteList(File input) throws IOException {
    final THashMap<String, Set<String>> map = new THashMap<String, Set<String>>();
    BufferedReader reader = new BufferedReader(new FileReader(input));
    try {
      String line;
      Set<String> currentClasses = new THashSet<String>();
      map.put("", currentClasses);
      while ((line = reader.readLine()) != null) {
        if (line.startsWith(".")) {
          String packageName = line.substring(1);
          currentClasses = new THashSet<String>();
          map.put(packageName, currentClasses);
        }
        else {
          currentClasses.add(line);
        }
      }
    }
    finally {
      reader.close();
    }
    return map;
  }

  public static Map<String, Set<String>> computeWhiteList(final File toolsApiJarFile) {
    try {
      final THashMap<String, Set<String>> map = new THashMap<String, Set<String>>();
      ClassLoader loader = new UrlClassLoader(Collections.singletonList(toolsApiJarFile.toURI().toURL()), AppEngineSdkUtil.class.getClassLoader());
      final Class<?> whiteListClass = Class.forName("com.google.apphosting.runtime.security.WhiteList", true, loader);
      final Set<String> classes = (Set<String>) whiteListClass.getMethod("getWhiteList").invoke(null);
      for (String qualifiedName : classes) {
        final String packageName = StringUtil.getPackageName(qualifiedName);
        Set<String> classNames = map.get(packageName);
        if (classNames == null) {
          classNames = new THashSet<String>();
          map.put(packageName, classNames);
        }
        classNames.add(StringUtil.getShortName(qualifiedName));
      }
      return map;
    }
    catch (Exception e) {
      LOG.error(e);
      return Collections.emptyMap();
    }
  }
}
