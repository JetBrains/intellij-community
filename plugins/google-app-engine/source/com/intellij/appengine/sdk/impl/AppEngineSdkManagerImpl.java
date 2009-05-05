package com.intellij.appengine.sdk.impl;

import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class AppEngineSdkManagerImpl extends AppEngineSdkManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.sdk.impl.AppEngineSdkManagerImpl");
  private Map<String, Set<String>> myClassesWhiteList;
  private Map<String, Set<String>> myMethodsBlackList;

  @Override
  public boolean isClassInWhiteList(@NotNull String className) {
    if (myClassesWhiteList == null) {
      try {
        myClassesWhiteList = loadWhiteList();
      }
      catch (IOException e) {
        LOG.error(e);
        myClassesWhiteList = new THashMap<String, Set<String>>();
      }
    }
    final String packageName = StringUtil.getPackageName(className);
    final String name = StringUtil.getShortName(className);
    final Set<String> classes = myClassesWhiteList.get(packageName);
    return classes != null && classes.contains(name);
  }

  @Override
  public boolean isMethodInBlacklist(@NotNull String className, @NotNull String methodName) {
    if (myMethodsBlackList == null) {
      try {
        myMethodsBlackList = loadBlackList();
      }
      catch (IOException e) {
        LOG.error(e);
        myMethodsBlackList = new THashMap<String, Set<String>>();
      }
    }
    final Set<String> methods = myMethodsBlackList.get(className);
    return methods != null && methods.contains(methodName);
  }

  private Map<String, Set<String>> loadBlackList() throws IOException {
    final InputStream stream = getClass().getResourceAsStream("/data/methodsBlacklist.txt");
    BufferedReader reader = null;
    final THashMap<String, Set<String>> map = new THashMap<String, Set<String>>();
    try {
      reader = new BufferedReader(new InputStreamReader(stream));
      String line;
      while ((line = reader.readLine()) != null) {
        final int i = line.indexOf(':');
        String className = line.substring(0, i);
        String methods = line.substring(i + 1);
        map.put(className, new THashSet<String>(StringUtil.split(methods, ",")));
      }
    }
    finally {
      if (reader != null) {
        reader.close();
      }
    }
    return map;
  }

  private Map<String, Set<String>> loadWhiteList() throws IOException {
    final InputStream stream = getClass().getResourceAsStream("/data/jreWhitelist.txt");
    BufferedReader reader = null;
    final THashMap<String, Set<String>> map = new THashMap<String, Set<String>>();
    try {
      reader = new BufferedReader(new InputStreamReader(stream));
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
      if (reader != null) {
        reader.close();
      }
    }
    return map;
  }
}
