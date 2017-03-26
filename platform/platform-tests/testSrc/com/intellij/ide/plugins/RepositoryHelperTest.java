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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.MultiMap;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;

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
  public void testBrokenNotInList() throws Exception {
    Method versionsGetter = ReflectionUtil.getDeclaredMethod(PluginManagerCore.class, "getBrokenPluginVersions");
    versionsGetter.setAccessible(true);
    MultiMap<String, String> versions = (MultiMap<String, String>)versionsGetter.invoke(null);
    try {
      versions.putValue("a.broken.plugin", "1.0.5");
      List<IdeaPluginDescriptor> list = loadPlugins(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<plugin-repository>\n" +
        "  <ff>\"J2EE\"</ff>\n" +
        "  <category name=\"J2EE\">\n" +
        "    <idea-plugin downloads=\"1\" size=\"1024\" date=\"1119060380000\" url=\"\">\n" +
        "      <name>AWS Manager</name>\n" +
        "      <id>a.broken.plugin</id>\n" +
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
      assertEquals(1, list.size());
    } finally {
      versions.remove("a.broken.plugin", "1.0.5");
    }
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

  private List<IdeaPluginDescriptor> loadPlugins(String data) throws IOException {
    File tempFile = tempDir.newFile("repo.xml");
    FileUtil.writeToFile(tempFile, data);
    String url = tempFile.toURI().toURL().toString();
    return RepositoryHelper.loadPlugins(url, null);
  }
}