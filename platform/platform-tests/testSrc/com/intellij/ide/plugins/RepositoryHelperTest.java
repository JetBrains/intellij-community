// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.rules.TempDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class RepositoryHelperTest {
  @Rule
  public TempDirectory tempDir = new TempDirectory();

  @ClassRule
  public static final ApplicationRule appRule = new ApplicationRule();

  @Test(expected = IOException.class)
  public void testEmpty() throws IOException {
    loadPlugins("");
  }

  @Test
  public void testWrongFormat() throws IOException {
    List<PluginNode> list = loadPlugins("<?xml version=\"1.0\" encoding=\"UTF-8\"?><root/>");
    assertTrue(list.isEmpty());
  }

  @Test(expected = IOException.class)
  public void testFormatErrors() throws IOException {
    List<PluginNode> list = loadPlugins("<?xml version=\"1.0\" encoding=\"UTF-8\"?><id>42</id>");
    assertTrue(list.isEmpty());
  }

  @Test
  public void testFullFormat() throws IOException {
    List<PluginNode> list = loadPlugins(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <plugin-repository>
          <ff>"J2EE"</ff>
          <category name="J2EE">
            <idea-plugin downloads="1" size="1024" date="1119060380000" url="">
              <name>AWS Manager</name>
              <id>com.jetbrains.ec2manager2</id>
              <description>...</description>
              <version>1.0.5</version>
              <vendor email="michael.golubev@jetbrains.com" url="http://www.jetbrains.com">JetBrains</vendor>
              <idea-version min="n/a" max="n/a" since-build="133.193"/>
              <change-notes>...</change-notes>
              <depends>com.intellij.javaee</depends>
              <rating>3.5</rating>
              <download-url>plugin.zip</download-url>
            </idea-plugin>
            <idea-plugin downloads="6182" size="131276" date="1386612959000" url="">
              <name>tc Server Support</name>
              <id>com.intellij.tc.server</id>
              <description>...</description>
              <version>1.2</version>
              <vendor email="" url="http://www.jetbrains.com">JetBrains</vendor>
              <idea-version min="n/a" max="n/a" since-build="133.193"/>
              <change-notes>...</change-notes>
              <depends>com.intellij.javaee</depends>
              <rating>00</rating>
              <downloadUrl>plugin.zip</downloadUrl>
            </idea-plugin>  </category>
        </plugin-repository>""");
    assertEquals("Loaded plugins: " + StringUtil.join(list, IdeaPluginDescriptor::getName, ", "), 2, list.size());
  }

  @Test
  public void testBrokenNotInList() throws IOException {
    String id = "BrokenPlugin";
    String version = "1.0";
    Map<PluginId, Set<String>> brokenPluginsMap = Collections.singletonMap(PluginId.getId(id), Collections.singleton(version));
    PluginManagerCore.updateBrokenPlugins(brokenPluginsMap);

    List<PluginNode> list = loadPlugins(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<plugin-repository>\n" +
      "  <category name=\"Whatever\">\n" +
      "    <idea-plugin>\n" +
      "      <id>" + id + "</id>\n" +
      "      <version>" + version + "</version>\n" +
      "      <download-url>plugin.zip</download-url>\n" +
      "    </idea-plugin>\n" +
      "    <idea-plugin>\n" +
      "      <id>good.plugin</id>\n" +
      "      <version>1.0</version>\n" +
      "      <download-url>plugin.zip</download-url>\n" +
      "    </idea-plugin>" +
      "  </category>\n" +
      "</plugin-repository>");
    assertEquals("Failed on '" + id + ':' + version + "'", 1, list.size());
  }

  @Test
  public void testSimpleFormat() throws IOException {
    List<PluginNode> list = loadPlugins(
      """
        <plugins>
          <plugin id="my.plugin.1" url="plugin1.zip" version="1.2.3"/>
          <plugin id="my.plugin.2" url="plugin2.jar" version="4.5.6">
            <description>...</description>
            <depends>my.plugin.1</depends>
          </plugin>
          <plugin name="broken"/>
        </plugins>""");
    assertEquals(2, list.size());
  }

  @Test
  public void testSimpleFormatWithProlog() throws IOException {
    List<PluginNode> list = loadPlugins(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <plugins>
          <plugin id="org.jetbrains.kotlin" url="kotlin-plugin-0.9.999.zip" version="0.9.999" />
        </plugins>
        """);
    assertEquals(1, list.size());
  }

  @Test
  public void testEqualityById() throws IOException {
    IdeaPluginDescriptor node1 = loadPlugins("<plugins>\n<plugin id=\"ID\" url=\"plugin.zip\"><name>A</name></plugin>\n</plugins>").get(0);
    FileUtil.delete(new File(tempDir.getRoot(), "repo.xml"));
    IdeaPluginDescriptor node2 = loadPlugins("<plugins>\n<plugin id=\"ID\" url=\"plugin.zip\"><name>B</name></plugin>\n</plugins>").get(0);
    assertEquals(node1, node2);
    assertEquals(node1.hashCode(), node2.hashCode());
    assertNotEquals(node1.getName(), node2.getName());
  }

  @Test
  public void testListFiltering() throws IOException {
    int current = PluginManagerCore.getBuildNumber().getComponents()[0], next = current + 1;
    List<PluginNode> list = loadPlugins(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<plugin-repository>\n" +
      "  <category name=\"Test\">\n" +
      "    <idea-plugin downloads=\"0\" size=\"1\" date=\"1563793797845\" url=\"\">\n" +
      "      <name>Test Plugin</name>\n" +
      "      <id>com.jetbrains.test.plugin</id>\n" +
      "      <version>0.1</version>\n" +
      "      <idea-version since-build=\"" + current + ".0\" until-build=\"" + current + ".*\"/>\n" +
      "      <download-url>plugin.zip</download-url>\n" +
      "    </idea-plugin>\n" +
      "    <idea-plugin downloads=\"0\" size=\"1\" date=\"1563793797845\" url=\"\">\n" +
      "      <name>Test Plugin</name>\n" +
      "      <id>com.jetbrains.test.plugin</id>\n" +
      "      <version>0.2</version>\n" +
      "      <idea-version since-build=\"" + next + ".0\" until-build=\"" + next + ".*\"/>\n" +
      "      <download-url>plugin.zip</download-url>\n" +
      "    </idea-plugin>\n" +
      "  </category>\n" +
      "</plugin-repository>", BuildNumber.fromString(next + ".100"));
    assertEquals(1, list.size());
    assertEquals("0.2", list.get(0).getVersion());
  }

  private @NotNull List<PluginNode> loadPlugins(@NotNull String data) throws IOException {
    return loadPlugins(data, null);
  }

  private @NotNull List<PluginNode> loadPlugins(@NotNull String data,
                                                @Nullable BuildNumber build) throws IOException {
    File tempFile = tempDir.newFile("repo.xml");
    FileUtil.writeToFile(tempFile, data);
    String url = tempFile.toURI().toURL().toString();
    return RepositoryHelper.loadPlugins(url, build, null);
  }
}