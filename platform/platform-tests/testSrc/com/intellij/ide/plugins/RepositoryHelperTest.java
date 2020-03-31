// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.execution.ParametersListUtil;
import org.junit.Rule;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class RepositoryHelperTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test(expected = IOException.class)
  public void testEmpty() throws IOException {
    loadPlugins("");
  }

  @Test
  public void testWrongFormat() throws IOException {
    List<IdeaPluginDescriptor> list = loadPlugins("<?xml version=\"1.0\" encoding=\"UTF-8\"?><root/>");
    assertEquals(0, list.size());
  }

  @Test(expected = IOException.class)
  public void testFormatErrors() throws IOException {
    List<IdeaPluginDescriptor> list = loadPlugins("<?xml version=\"1.0\" encoding=\"UTF-8\"?><id>42</id>");
    assertEquals(0, list.size());
  }

  @Test
  public void testFullFormat() throws IOException {
    List<IdeaPluginDescriptor> list = loadPlugins(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<plugin-repository>\n" +
      "  <ff>\"J2EE\"</ff>\n" +
      "  <category name=\"J2EE\">\n" +
      "    <idea-plugin downloads=\"1\" size=\"1024\" date=\"1119060380000\" url=\"\">\n" +
      "      <name>AWS Manager</name>\n" +
      "      <id>com.jetbrains.ec2manager2</id>\n" +
      "      <description>...</description>\n" +
      "      <version>1.0.5</version>\n" +
      "      <vendor email=\"michael.golubev@jetbrains.com\" url=\"http://www.jetbrains.com\">JetBrains</vendor>\n" +
      "      <idea-version min=\"n/a\" max=\"n/a\" since-build=\"133.193\"/>\n" +
      "      <change-notes>...</change-notes>\n" +
      "      <depends>com.intellij.javaee</depends>\n" +
      "      <rating>3.5</rating>\n" +
      "      <download-url>plugin.zip</download-url>\n" +
      "    </idea-plugin>\n" +
      "    <idea-plugin downloads=\"6182\" size=\"131276\" date=\"1386612959000\" url=\"\">\n" +
      "      <name>tc Server Support</name>\n" +
      "      <id>com.intellij.tc.server</id>\n" +
      "      <description>...</description>\n" +
      "      <version>1.2</version>\n" +
      "      <vendor email=\"\" url=\"http://www.jetbrains.com\">JetBrains</vendor>\n" +
      "      <idea-version min=\"n/a\" max=\"n/a\" since-build=\"133.193\"/>\n" +
      "      <change-notes>...</change-notes>\n" +
      "      <depends>com.intellij.javaee</depends>\n" +
      "      <rating>00</rating>\n" +
      "      <downloadUrl>plugin.zip</downloadUrl>\n" +
      "    </idea-plugin>" +
      "  </category>\n" +
      "</plugin-repository>");
    assertEquals("Loaded plugins: " + StringUtil.join(list, IdeaPluginDescriptor::getName, ", "), 2, list.size());
  }

  @Test
  public void testBrokenNotInList() throws IOException {
    String id, version;
    try (InputStream resource = PluginManagerCore.class.getResourceAsStream("/brokenPlugins.txt");
         BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
      List<String> lines = reader.lines().filter(l -> !l.startsWith("//")).collect(Collectors.toList());
      List<String> tokens = ParametersListUtil.parse(lines.get(new Random().nextInt(lines.size())));
      id = tokens.get(0);
      version = tokens.get(1);
    }

    List<IdeaPluginDescriptor> list = loadPlugins(
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
    List<IdeaPluginDescriptor> list = loadPlugins(
      "<plugins>\n" +
      "  <plugin id=\"my.plugin.1\" url=\"plugin1.zip\" version=\"1.2.3\"/>\n" +
      "  <plugin id=\"my.plugin.2\" url=\"plugin2.jar\" version=\"4.5.6\">\n" +
      "    <description>...</description>\n" +
      "    <depends>my.plugin.1</depends>\n" +
      "  </plugin>\n" +
      "  <plugin name=\"broken\"/>\n" +
      "</plugins>");
    assertEquals(2, list.size());
  }

  @Test
  public void testSimpleFormatWithProlog() throws IOException {
    List<IdeaPluginDescriptor> list = loadPlugins(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<plugins>\n" +
      "  <plugin id=\"org.jetbrains.kotlin\" url=\"kotlin-plugin-0.9.999.zip\" version=\"0.9.999\" />\n" +
      "</plugins>\n");
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
    List<IdeaPluginDescriptor> list = loadPlugins(
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

  private List<IdeaPluginDescriptor> loadPlugins(String data) throws IOException {
    return loadPlugins(data, null);
  }

  private List<IdeaPluginDescriptor> loadPlugins(String data, BuildNumber build) throws IOException {
    File tempFile = tempDir.newFile("repo.xml");
    FileUtil.writeToFile(tempFile, data);
    String url = tempFile.toURI().toURL().toString();
    return RepositoryHelper.loadPlugins(url, build, null);
  }
}