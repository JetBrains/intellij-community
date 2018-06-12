// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.util.lang.UrlClassLoader;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.NoSuchElementException;

import static org.junit.Assert.*;

/**
 * @author Dmitry Avdeev
 */
public class PluginDescriptorTest {
  private static String getTestDataPath() {
    return PathManagerEx.getTestDataPath() + "/ide/plugins/pluginDescriptor";
  }

  @Test
  public void testDescriptorLoading() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("asp.jar");
    assertNotNull(descriptor);
    assertEquals("com.jetbrains.plugins.asp", descriptor.getPluginId().getIdString());
    assertEquals("ASP", descriptor.getName());
  }

  @Test
  public void testOptionalDescriptors() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("family");
    assertNotNull(descriptor);
    assertEquals(1, descriptor.getOptionalDescriptors().size());
  }

  @Test
  public void testMalformedDescriptor() {
    assertNull(loadDescriptor("malformed"));
  }

  @Test
  public void testAnonymousDescriptor() {
    assertNull(loadDescriptor("anonymous"));
  }

  @Test
  public void testFilteringDuplicates() throws MalformedURLException {
    URL[] urls = {
      new File(getTestDataPath(), "duplicate1.jar").toURI().toURL(),
      new File(getTestDataPath(), "duplicate2.jar").toURI().toURL()};
    assertEquals(1, PluginManagerCore.testLoadDescriptorsFromClassPath(new URLClassLoader(urls, null)).size());
  }

  @Test
  public void testPluginNameAsId() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("noId");
    assertNotNull(descriptor);
    assertEquals(descriptor.getName(), descriptor.getPluginId().getIdString());
  }

  @Test
  public void testPluginIdAsName() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("noName");
    assertNotNull(descriptor);
    assertEquals(descriptor.getPluginId().getIdString(), descriptor.getName());
  }

  @Test
  public void testUrlTolerance() throws MalformedURLException {
    class SingleUrlEnumeration implements Enumeration<URL> {
      private final URL myUrl;
      private boolean hasMoreElements = true;

      public SingleUrlEnumeration(URL url) {
        myUrl = url;
      }

      @Override
      public boolean hasMoreElements() {
        return hasMoreElements;
      }

      @Override
      public URL nextElement() {
        if (!hasMoreElements) throw new NoSuchElementException();
        hasMoreElements = false;
        return myUrl;
      }
    }

    class TestLoader extends UrlClassLoader {
      private final URL myUrl;

      public TestLoader(String prefix, String suffix) throws MalformedURLException {
        super(build());
        myUrl = new URL(prefix + new File(getTestDataPath()).toURI().toURL().toString() + suffix + "META-INF/plugin.xml");
      }

      @Override
      public URL getResource(String name) {
        return null;
      }

      @Override
      public Enumeration<URL> getResources(String name) {
        return new SingleUrlEnumeration(myUrl);
      }
    }

    ClassLoader loader1 = new TestLoader("", "/spaces%20spaces/");
    assertEquals(1, PluginManagerCore.testLoadDescriptorsFromClassPath(loader1).size());

    ClassLoader loader2 = new TestLoader("", "/spaces spaces/");
    assertEquals(1, PluginManagerCore.testLoadDescriptorsFromClassPath(loader2).size());

    ClassLoader loader3 = new TestLoader("jar:", "/jar%20spaces.jar!/");
    assertEquals(1, PluginManagerCore.testLoadDescriptorsFromClassPath(loader3).size());

    ClassLoader loader4 = new TestLoader("jar:", "/jar spaces.jar!/");
    assertEquals(1, PluginManagerCore.testLoadDescriptorsFromClassPath(loader4).size());
  }

  private static IdeaPluginDescriptorImpl loadDescriptor(String dirName) {
    try {
      File dir = new File(getTestDataPath(), dirName);
      assertTrue(dir + " does not exist", dir.exists());
      return PluginManagerCore.loadDescriptor(dir, PluginManagerCore.PLUGIN_XML);
    }
    catch (AssertionError e) {
      assertTrue(e.getMessage(), e.getMessage().contains("Problems found loading plugins"));
      return null;
    }
  }
}