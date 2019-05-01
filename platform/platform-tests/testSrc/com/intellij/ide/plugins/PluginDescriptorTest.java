// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.lang.UrlClassLoader;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Set;

import static com.intellij.testFramework.UsefulTestCase.assertEmpty;
import static com.intellij.testFramework.UsefulTestCase.assertOneElement;
import static org.junit.Assert.*;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings("SuspiciousPackagePrivateAccess")
public class PluginDescriptorTest {
  private static String getTestDataPath() {
    return PathManagerEx.getTestDataPath() + "/ide/plugins/pluginDescriptor";
  }

  @Rule public TempDirectory tempDir = new TempDirectory();

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
  public void testMultipleOptionalDescriptors() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("multipleOptionalDescriptors");
    assertNotNull(descriptor);
    Set<PluginId> ids = descriptor.getOptionalDescriptors().keySet();
    assertEquals(2, ids.size());
    PluginId[] idsArray = ids.toArray(PluginId.EMPTY_ARRAY);
    assertEquals("dep2", idsArray[0].getIdString());
    assertEquals("dep1", idsArray[1].getIdString());
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
  public void testFilteringDuplicates() throws Exception {
    URL[] urls = {
      new File(getTestDataPath(), "duplicate1.jar").toURI().toURL(),
      new File(getTestDataPath(), "duplicate2.jar").toURI().toURL()};
    assertEquals(1, PluginManagerCore.testLoadDescriptorsFromClassPath(new URLClassLoader(urls, null)).size());
  }

  @Test
  public void testDuplicateDependency() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("duplicateDependency");
    assertNotNull(descriptor);
    assertEmpty(descriptor.getOptionalDependentPluginIds() );
    assertEquals("foo",assertOneElement(descriptor.getDependentPluginIds()).getIdString());
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
  public void testUrlTolerance() throws Exception {
    class SingleUrlEnumeration implements Enumeration<URL> {
      private final URL myUrl;
      private boolean hasMoreElements = true;

      SingleUrlEnumeration(URL url) {
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

      TestLoader(String prefix, String suffix) throws MalformedURLException {
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

  @Test
  public void testEqualityById() throws IOException {
    File tempFile = tempDir.newFile(PluginManagerCore.PLUGIN_XML_PATH);
    FileUtil.writeToFile(tempFile, "<idea-plugin>\n<id>ID</id>\n<name>A</name>\n</idea-plugin>");
    IdeaPluginDescriptorImpl impl1 = loadDescriptor(tempDir.getRoot());
    FileUtil.writeToFile(tempFile, "<idea-plugin>\n<id>ID</id>\n<name>B</name>\n</idea-plugin>");
    IdeaPluginDescriptorImpl impl2 = loadDescriptor(tempDir.getRoot());
    assertEquals(impl1, impl2);
    assertEquals(impl1.hashCode(), impl2.hashCode());
    assertNotEquals(impl1.getName(), impl2.getName());
  }

  private static IdeaPluginDescriptorImpl loadDescriptor(String dirName) {
    return loadDescriptor(new File(getTestDataPath(), dirName));
  }

  private static IdeaPluginDescriptorImpl loadDescriptor(File dir) {
    assertTrue(dir + " does not exist", dir.exists());
    try {
      return PluginManagerCore.loadDescriptor(dir, PluginManagerCore.PLUGIN_XML);
    }
    catch (AssertionError e) {
      assertTrue(e.getMessage(), e.getMessage().contains("Problems found loading plugins"));
      return null;
    }
  }
}