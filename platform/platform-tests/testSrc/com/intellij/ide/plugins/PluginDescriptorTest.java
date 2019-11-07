// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.rules.InMemoryFsRule;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.intellij.testFramework.UsefulTestCase.*;
import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 * @author Dmitry Avdeev
 */
public class PluginDescriptorTest {
  private static String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "plugins/pluginDescriptor";
  }

  @Rule
  public final InMemoryFsRule inMemoryFs = new InMemoryFsRule();

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
    assertEquals(1, descriptor.optionalConfigs.size());
  }

  @Test
  public void testMultipleOptionalDescriptors() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("multipleOptionalDescriptors");
    assertNotNull(descriptor);
    Set<PluginId> ids = descriptor.optionalConfigs.keySet();
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
  public void testCyclicOptionalDeps() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("cyclicOptionalDeps");
    assertThat(descriptor).isNotNull();
    ArrayList<IdeaPluginDescriptorImpl> allOptionalDescriptors = new ArrayList<>();
    collectDescriptors(descriptor, allOptionalDescriptors);
    assertThat(allOptionalDescriptors).hasSize(2);
  }

  private static void collectDescriptors(@NotNull IdeaPluginDescriptorImpl rootDescriptor, @NotNull List<IdeaPluginDescriptorImpl> result) {
    Map<PluginId, List<Map.Entry<String, IdeaPluginDescriptorImpl>>> optionalConfigs = rootDescriptor.optionalConfigs;
    if (optionalConfigs == null) {
      return;
    }

    optionalConfigs.forEach((id, entries) -> {
      for (Map.Entry<String, IdeaPluginDescriptorImpl> entry : entries) {
        IdeaPluginDescriptorImpl descriptor = entry.getValue();
        if (descriptor != null) {
          result.add(descriptor);
          collectDescriptors(descriptor, result);
        }
      }
    });
  }

  @Test
  public void testFilteringDuplicates() throws Exception {
    URL[] urls = {
      Paths.get(getTestDataPath(), "duplicate1.jar").toUri().toURL(),
      Paths.get(getTestDataPath(), "duplicate2.jar").toUri().toURL()
    };
    assertThat(PluginManagerCore.testLoadDescriptorsFromClassPath(new URLClassLoader(urls, null))).hasSize(1);
  }

  @Test
  public void testProductionPlugins() throws Exception {
    Assume.assumeTrue(SystemInfo.isMac && !IS_UNDER_TEAMCITY);

    List<? extends IdeaPluginDescriptor> descriptors = PluginManagerCore.testLoadDescriptorsFromDir(Paths.get("/Applications/Idea.app/Contents/plugins"));
    assertThat(descriptors).isNotEmpty();
    assertThat(ContainerUtil.find(descriptors, it -> it.getPluginId().getIdString().equals("com.intellij.java"))).isNotNull();
  }

  @Test
  public void testProductionProductLib() throws Exception {
    Assume.assumeTrue(SystemInfo.isMac && !IS_UNDER_TEAMCITY);

    List<URL> urls = new ArrayList<>();
    for (File it : new File("/Applications/Idea.app/Contents/lib").listFiles()) {
      urls.add(it.toURI().toURL());
    }
    List<? extends IdeaPluginDescriptor> descriptors = PluginManagerCore.testLoadDescriptorsFromClassPath(new URLClassLoader(urls.toArray(new URL[0]), null));
    // core and com.intellij.workspace
    assertThat(descriptors).hasSize(1);
  }

  @Test
  public void testProduction2() throws Exception {
    Assume.assumeTrue(SystemInfo.isMac && !IS_UNDER_TEAMCITY);

    List<? extends IdeaPluginDescriptor> descriptors = PluginManagerCore.testLoadDescriptorsFromDir(Paths.get("/Volumes/data/plugins"));
    assertThat(descriptors).isNotEmpty();
  }

  @Test
  public void testDuplicateDependency() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("duplicateDependency");
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getOptionalDependentPluginIds()).isEmpty();
    assertThat(descriptor.getDependentPluginIds()).containsExactly(PluginId.getId("foo"));
  }

  @Test
  public void testPluginNameAsId() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("noId");
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getPluginId().getIdString()).isEqualTo(descriptor.getName());
  }

  @Test
  public void releaseDate() throws IOException {
    Path pluginFile = inMemoryFs.getFs().getPath("plugin/META-INF/plugin.xml");
    PathKt.write(pluginFile, "<idea-plugin>\n" +
                             "  <id>bar</id>\n" +
                             "  <product-descriptor code=\"IJ\" release-date=\"20190811\" release-version=\"42\"/>\n" +
                             "</idea-plugin>");
    IdeaPluginDescriptorImpl descriptor = loadDescriptor(pluginFile.getParent().getParent());
    assertThat(descriptor).isNotNull();
    assertThat(new SimpleDateFormat("yyyyMMdd", Locale.US).format(descriptor.getReleaseDate())).isEqualTo("20190811");
  }

  @Test
  public void componentConfig() throws IOException {
    Path pluginFile = inMemoryFs.getFs().getPath("/plugin/META-INF/plugin.xml");
    PathKt.write(pluginFile, "<idea-plugin>\n" +
                             "  <id>bar</id>\n" +
                             "  <project-components>\n" +
                             "    <component>\n" +
                             "      <implementation-class>com.intellij.ide.favoritesTreeView.FavoritesManager</implementation-class>\n" +
                             "      <option name=\"workspace\" value=\"true\"/>\n" +
                             "    </component>\n" +
                             "\n" +
                             "    \n" +
                             "  </project-components>\n" +
                             "</idea-plugin>");
    IdeaPluginDescriptorImpl descriptor = loadDescriptor(pluginFile.getParent().getParent());
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getProjectContainerDescriptor().components.get(0).options).isEqualTo(Collections.singletonMap("workspace", "true"));
  }

  @Test
  public void testPluginIdAsName() {
    IdeaPluginDescriptorImpl descriptor = loadDescriptor("noName");
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getName()).isEqualTo(descriptor.getPluginId().getIdString());
  }

  @Test
  public void testUrlTolerance() throws Exception {
    final class SingleUrlEnumeration implements Enumeration<URL> {
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
    assertThat(PluginManagerCore.testLoadDescriptorsFromClassPath(loader4)).hasSize(1);
  }

  @Test
  public void testEqualityById() throws IOException {
    FileSystem fs = inMemoryFs.getFs();
    Path tempFile = fs.getPath("/", PluginManagerCore.PLUGIN_XML_PATH);
    PathKt.write(tempFile, "<idea-plugin>\n<id>ID</id>\n<name>A</name>\n</idea-plugin>");
    IdeaPluginDescriptorImpl impl1 = loadDescriptor(fs.getPath("/"));
    PathKt.write(tempFile, "<idea-plugin>\n<id>ID</id>\n<name>B</name>\n</idea-plugin>");
    IdeaPluginDescriptorImpl impl2 = loadDescriptor(fs.getPath("/"));
    assertEquals(impl1, impl2);
    assertEquals(impl1.hashCode(), impl2.hashCode());
    assertNotSame(impl1.getName(), impl2.getName());
  }

  private static IdeaPluginDescriptorImpl loadDescriptor(String dirName) {
    return loadDescriptor(Paths.get(getTestDataPath(), dirName));
  }

  private static IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path dir) {
    assertThat(dir).exists();
    PluginManagerCore.ourPluginError = null;
    IdeaPluginDescriptorImpl result = PluginManager.loadDescriptor(dir, PluginManagerCore.PLUGIN_XML, Collections.emptySet());
    if (result == null) {
      assertThat(PluginManagerCore.ourPluginError).isNotNull();
      PluginManagerCore.ourPluginError = null;
    }
    return result;
  }
}