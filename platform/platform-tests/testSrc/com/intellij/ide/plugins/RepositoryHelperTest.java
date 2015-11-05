/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.IoTestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RepositoryHelperTest {
  private File myTempFile;

  @Before
  public void setUp() throws Exception {
    myTempFile = IoTestUtil.createTestFile("repo.xml");
  }

  @After
  public void tearDown() throws Exception {
    FileUtil.delete(myTempFile);
  }

  @Test(expected = IOException.class)
  public void testEmpty() throws IOException {
    loadPlugins("");
  }

  @Test
  public void testWrongFormat() throws IOException {
    List<IdeaPluginDescriptor> list = loadPlugins("<?xml version=\"1.0\" encoding=\"UTF-8\"?><root/>");
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
      "      <id>com.jetbrains.ec2manager</id>\n" +
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
    assertEquals(2, list.size());
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
    FileUtil.writeToFile(myTempFile, data);
    String url = myTempFile.toURI().toURL().toString();
    return RepositoryHelper.loadPlugins(url, null);
  }
}
