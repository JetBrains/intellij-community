/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    File file = new File(getTestDataPath(), "asp.jar");
    assertTrue(file + " not exist", file.exists());
    IdeaPluginDescriptorImpl descriptor = PluginManagerCore.loadDescriptor(file, PluginManagerCore.PLUGIN_XML);
    assertNotNull(descriptor);
  }

  @Test
  public void testInvalidFileDescriptor() {
    File file = new File(getTestDataPath(), "malformed");
    assertTrue(file + " not exist", file.exists());
    IdeaPluginDescriptorImpl descriptor = PluginManagerCore.loadDescriptor(file, PluginManagerCore.PLUGIN_XML);
    assertNull(descriptor);
  }

  @Test
  public void testFilteringDuplicates() throws MalformedURLException {
    URL[] urls = {
      new File(getTestDataPath(), "duplicate1.jar").toURI().toURL(),
      new File(getTestDataPath(), "duplicate2.jar").toURI().toURL()};
    assertEquals(1, PluginManagerCore.testLoadDescriptorsFromClassPath(new URLClassLoader(urls, null)).size());
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
}