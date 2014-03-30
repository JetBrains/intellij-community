package com.intellij.remoteServer.agent.impl;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author michael.golubev
 */
public class RemoteAgentClassLoaderCache {

  private final Map<Set<URL>, URLClassLoader> myUrls2ClassLoader = new HashMap<Set<URL>, URLClassLoader>();

  public URLClassLoader getOrCreateClassLoader(Set<URL> libraryUrls) {
    URLClassLoader result = myUrls2ClassLoader.get(libraryUrls);
    if (result == null) {
      result = new URLClassLoader(libraryUrls.toArray(new URL[libraryUrls.size()]), null);
      myUrls2ClassLoader.put(libraryUrls, result);
    }
    return result;
  }
}
