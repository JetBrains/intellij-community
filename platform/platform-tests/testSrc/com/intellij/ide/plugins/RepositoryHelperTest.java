// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.newui.PluginDtoModelBuilderFactory;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.ide.plugins.BrokenPluginFileKt.updateBrokenPlugins;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryHelperTest {
  @TempDir Path tempDir;

  @Test void testEmpty() {
    assertThrows(IOException.class, () -> {
      loadPlugins("");
    });
  }

  @Test void testWrongFormat() throws IOException {
    var list = loadPlugins("<?xml version=\"1.0\" encoding=\"UTF-8\"?><root/>");
    assertTrue(list.isEmpty());
  }

  @Test void testFormatErrors() {
    assertThrows(IOException.class, () -> {
      loadPlugins("<?xml version=\"1.0\" encoding=\"UTF-8\"?><id>42</id>");
    });
  }

  @Test void testFullFormat() throws IOException {
    var list = loadPlugins("""
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
            </idea-plugin>
          </category>
        </plugin-repository>"""
    );
    assertEquals(2, list.size(), "Loaded plugins: " + list);
  }

  @Test void testBrokenNotInList() throws IOException {
    var id = "BrokenPlugin";
    var version = "1.0";
    var brokenPluginsMap = Map.of(PluginId.getId(id), Set.of(version));
    updateBrokenPlugins(brokenPluginsMap);

    var list = loadPlugins("""
        <?xml version="1.0" encoding="UTF-8"?>
        <plugin-repository>
          <category name="Whatever">
            <idea-plugin>
              <id>%s</id>
              <version>%s</version>
              <download-url>plugin.zip</download-url>
            </idea-plugin>
            <idea-plugin>
              <id>good.plugin</id>
              <version>1.0</version>
              <download-url>plugin.zip</download-url>
            </idea-plugin>
          </category>
        </plugin-repository>""".formatted(id, version)
    );
    assertEquals(1, list.size(), "Failed on '" + id + ':' + version + "'");
  }

  @Test void testSimpleFormat() throws IOException {
    var list = loadPlugins("""
        <plugins>
          <plugin id="my.plugin.1" url="plugin1.zip" version="1.2.3"/>
          <plugin id="my.plugin.2" url="plugin2.jar" version="4.5.6">
            <description>...</description>
            <depends>my.plugin.1</depends>
          </plugin>
          <plugin name="broken"/>
        </plugins>"""
    );
    assertEquals(2, list.size());
  }

  @Test void testSimpleFormatWithProlog() throws IOException {
    var list = loadPlugins("""
        <?xml version="1.0" encoding="UTF-8"?>
        <plugins>
          <plugin id="org.jetbrains.kotlin" url="kotlin-plugin-0.9.999.zip" version="0.9.999" />
        </plugins>"""
    );
    assertEquals(1, list.size());
  }

  @Test void testEqualityById() throws IOException {
    var node1 = loadPlugins("<plugins>\n<plugin id=\"ID\" url=\"plugin.zip\"><name>A</name></plugin>\n</plugins>").getFirst();
    Files.delete(tempDir.resolve("repo.xml"));
    var node2 = loadPlugins("<plugins>\n<plugin id=\"ID\" url=\"plugin.zip\"><name>B</name></plugin>\n</plugins>").getFirst();
    assertEquals(node1, node2);
    assertEquals(node1.hashCode(), node2.hashCode());
    assertNotEquals(node1.getName(), node2.getName());
  }

  @Test void testListFiltering() throws IOException {
    int current = PluginManagerCore.getBuildNumber().getComponents()[0], next = current + 1;
    var list = loadPlugins("""
        <?xml version="1.0" encoding="UTF-8"?>
        <plugin-repository>
          <category name="Test">
            <idea-plugin downloads="0" size="1" date="1563793797845" url="">
              <name>Test Plugin</name>
              <id>com.jetbrains.test.plugin</id>
              <version>0.1</version>
              <idea-version since-build="%d.0" until-build="%d.*"/>
              <download-url>plugin.zip</download-url>
            </idea-plugin>
            <idea-plugin downloads="0" size="1" date="1563793797845" url="">
              <name>Test Plugin</name>
              <id>com.jetbrains.test.plugin</id>
              <version>0.2</version>
              <idea-version since-build="%d.0" until-build="%d.*"/>
              <download-url>plugin.zip</download-url>
            </idea-plugin>
          </category>
        </plugin-repository>""".formatted(current, current, next, next), BuildNumber.fromString(next + ".100")
    );
    assertEquals(1, list.size());
    assertEquals("0.2", list.getFirst().getVersion());
  }

  @Test void testListFilteringWithPluginDtoModelBuilder(@TempDir Path tempDir) throws IOException {
    @Language("XML") var data = """
      <plugins>
        <plugin id="com.jetbrains.test.plugin" url="plugin-253.zip" version="1.0.0-253">
          <idea-version since-build="253" until-build="253.*"/>
        </plugin>
        <plugin id="com.jetbrains.test.plugin" url="plugin-261.zip" version="1.0.0-261">
          <idea-version since-build="261" until-build="261.*"/>
        </plugin>
        <plugin id="com.jetbrains.test.plugin" url="plugin-262.zip" version="1.0.0-262">
          <idea-version since-build="262" until-build="262.*"/>
        </plugin>
      </plugins>""";
    var tempFile = Files.writeString(tempDir.resolve("repo.xml"), data);
    var url = tempFile.toUri().toURL().toString();
    var list = RepositoryHelper.loadPluginModels(url, BuildNumber.fromString("261.100"), null, PluginDtoModelBuilderFactory.INSTANCE);
    assertEquals(1, list.size());
    assertEquals("1.0.0-261", list.getFirst().getVersion());
  }

  private List<PluginUiModel> loadPlugins(@Language("XML") String data) throws IOException {
    return loadPlugins(data, null);
  }

  private List<PluginUiModel> loadPlugins(@Language("XML") String data, @Nullable BuildNumber build) throws IOException {
    var tempFile = Files.writeString(tempDir.resolve("repo.xml"), data);
    var url = tempFile.toUri().toURL().toString();
    return RepositoryHelper.loadPluginModels(url, build, null);
  }
}
