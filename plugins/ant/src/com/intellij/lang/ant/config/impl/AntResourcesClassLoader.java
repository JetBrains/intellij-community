package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.lang.UrlClassLoader;

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
*         Date: Oct 21, 2008
*/
public class AntResourcesClassLoader extends UrlClassLoader {
  private final ProgressManager myPm = ProgressManager.getInstance();
  private final Set<String> myMisses = new HashSet<String>();

  public AntResourcesClassLoader(final List<URL> urls, final ClassLoader parentLoader, final boolean canLockJars, final boolean canUseCache) {
    super(urls, parentLoader, canLockJars, canUseCache);
  }

  protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
    if (myMisses.contains(name)) {
      throw new ClassNotFoundException(name);
    }
    return super.loadClass(name, resolve);
  }

  protected Class findClass(final String name) throws ClassNotFoundException {
    myPm.checkCanceled();
    try {
      return super.findClass(name);
    }
    catch (ClassNotFoundException e) {
      myMisses.add(name);
      throw e;
    }
  }
}
