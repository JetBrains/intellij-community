// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.TriConsumer;
import com.intellij.util.xml.dom.XmlDomReader;
import com.intellij.util.xml.dom.XmlElement;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static com.intellij.ide.plugins.DynamicPluginsTestUtil.createPluginLoadingResult;
import static com.intellij.ide.plugins.DynamicPluginsTestUtil.loadDescriptorInTest;
import static com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported;
import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.*;

public class PluginManagerTest {
  private static String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "plugins/sort";
  }

  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void compatibilityBranchBased() {
    assertCompatible("145.2", null, null);
    assertCompatible("145.2.2", null, null);

    assertCompatible("145.2", "145", null);
    assertCompatible("145.2", null, "146");
    assertCompatible("145.2.2", "145", null);
    assertCompatible("145.2.2", null, "146");
    assertIncompatible("145.2", null, "145");

    assertIncompatible("145.2", "146", null);
    assertIncompatible("145.2", null, "144");
    assertIncompatible("145.2.2", "146", null);
    assertIncompatible("145.2.2", null, "144");

    assertCompatible("145.2", "145.2", null);
    assertCompatible("145.2", null, "145.2");
    assertCompatible("145.2.2", "145.2", null);
    assertIncompatible("145.2.2", null, "145.2");

    assertIncompatible("145.2", "145.3", null);
    assertIncompatible("145.2", null, "145.1");
    assertIncompatible("145.2.2", "145.3", null);
    assertIncompatible("145.2.2", null, "145.1");

    assertCompatible("145.2", "140.3", null);
    assertCompatible("145.2", null, "146.1");
    assertCompatible("145.2.2", "140.3", null);
    assertCompatible("145.2.2", null, "146.1");

    assertIncompatible("145.2", "145.2.0", null);
    assertIncompatible("145.2", "145.2.1", null);
    assertCompatible("145.2", null, "145.2.3");
    assertCompatible("145.2.2", "145.2.0", null);
    assertCompatible("145.2.2", null, "145.2.3");
  }

  @Test
  public void ignoredCompatibility() {
    TriConsumer<String, String, String> checkCompatibility = (String ideVersion, String sinceBuild, String untilBuild) -> {
      boolean ignoreCompatibility = PluginManagerCore.isIgnoreCompatibility;
      try {
        assertIncompatible(ideVersion, sinceBuild, untilBuild);

        PluginManagerCore.isIgnoreCompatibility = true;
        assertCompatible(ideVersion, sinceBuild, untilBuild);
      }
      finally {
        PluginManagerCore.isIgnoreCompatibility = ignoreCompatibility;
      }
    };

    checkCompatibility.accept("42", "43", null);
    checkCompatibility.accept("43", null, "42");
  }

  @Test
  public void compatibilityBranchBasedStar() {
    assertCompatible("145.10", "144.*", null);
    assertIncompatible("145.10", "145.*", null);
    assertIncompatible("145.10", "146.*", null);
    assertIncompatible("145.10", null, "144.*");
    assertCompatible("145.10", null, "145.*");
    assertCompatible("145.10", null, "146.*");

    assertCompatible("145.10.1", null, "145.*");
    assertCompatible("145.10.1", "145.10", "145.10.*");

    assertCompatible("145.SNAPSHOT", null, "145.*");
  }

  @Test
  public void compatibilitySnapshots() {
    assertIncompatible("145.SNAPSHOT", "146", null);
    assertIncompatible("145.2.SNAPSHOT", "145.3", null);

    assertCompatible("145.SNAPSHOT", "145.2", null);

    assertCompatible("145.SNAPSHOT", null, "146");
    assertIncompatible("145.SNAPSHOT", null, "145");
    assertIncompatible("145.SNAPSHOT", null, "144");
    assertIncompatible("145.2.SNAPSHOT", null, "145");
    assertIncompatible("145.2.SNAPSHOT", null, "144");
  }

  @Test
  public void compatibilityPlatform() {
    assertEquals(SystemInfo.isWindows, checkCompatibility("com.intellij.modules.os.windows"));
    assertEquals(SystemInfo.isMac, checkCompatibility("com.intellij.modules.os.mac"));
    assertEquals(SystemInfo.isLinux, checkCompatibility("com.intellij.modules.os.linux"));
    assertEquals(SystemInfo.isFreeBSD, checkCompatibility("com.intellij.modules.os.freebsd"));
    assertEquals(SystemInfo.isSolaris, checkCompatibility("com.intellij.modules.os.solaris"));
    assertEquals(SystemInfo.isUnix, checkCompatibility("com.intellij.modules.os.unix"));
    assertEquals(SystemInfo.isUnix && !SystemInfo.isMac, checkCompatibility("com.intellij.modules.os.xwindow"));
  }

  @Test
  public void convertExplicitBigNumberInUntilBuildToStar() {
    assertConvertsTo(null, null);
    assertConvertsTo("145", "145");
    assertConvertsTo("145.999", "145.999");
    assertConvertsTo("145.9999", "145.*");
    assertConvertsTo("145.99999", "145.*");
    assertConvertsTo("145.9999.1", "145.9999.1");
    assertConvertsTo("145.1000", "145.1000");
    assertConvertsTo("145.10000", "145.*");
    assertConvertsTo("145.100000", "145.*");
  }

  @Test
  public void testSimplePluginSort() throws Exception {
     doPluginSortTest("simplePluginSort", false);
  }

  /*
   Actual result:
   HTTP Client (main)
   Endpoints (main)
   HTTP Client (intellij.restClient.microservicesUI, depends on Endpoints)

   Expected:
   Endpoints (main)
   HTTP Client (main)
   HTTP Client (intellij.restClient.microservicesUI, depends on Endpoints)

   But graph is correct - HTTP Client (main) it is node that doesn't depend on Endpoints (main),
   so no reason for DFSTBuilder to put it after.
   See CachingSemiGraph.getSortedPlugins for a solution.
  */
  @Test
  public void moduleSort() throws Exception {
     doPluginSortTest("moduleSort", true);
  }

  @Test
  public void testUltimatePlugins() throws Exception {
    doPluginSortTest("ultimatePlugins", true);
  }

  @Test
  public void testModulePluginIdContract() {
    var pluginsPath = Path.of(PlatformTestUtil.getPlatformTestDataPath(), "plugins", "withModules");
    var descriptorBundled = loadDescriptorInTest(pluginsPath, true);
    var pluginSet = new PluginSetBuilder(Set.of(descriptorBundled)).createPluginSetWithEnabledModulesMap();

    var moduleId = PluginId.getId("foo.bar");
    var corePlugin = PluginId.getId("my.plugin");
    assertThat(pluginSet.findEnabledPlugin(moduleId).getPluginId()).isEqualTo(corePlugin);
  }

  @Test
  public void testIdentifyPreInstalledPlugins() {
    var pluginsPath = Path.of(PlatformTestUtil.getPlatformTestDataPath(), "plugins", "updatedBundled");
    var bundled = loadDescriptorInTest(pluginsPath.resolve("bundled"), true);
    var updated = loadDescriptorInTest(pluginsPath.resolve("updated"));
    var expectedPluginId = updated.getPluginId();
    assertEquals(expectedPluginId, bundled.getPluginId());

    assertPluginPreInstalled(expectedPluginId, bundled, updated);
    assertPluginPreInstalled(expectedPluginId, updated, bundled);
  }

  @Test
  public void testSymlinkInConfigPath() throws IOException {
    assumeSymLinkCreationIsSupported();

    var configPath = tempDir.getRoot().toPath().resolve("config-link");
    var target = tempDir.newDirectory("config-target").toPath();
    Files.createSymbolicLink(configPath, target);
    DisabledPluginsState.Companion.saveDisabledPluginsAndInvalidate(configPath, List.of("a"));
    assertThat(configPath.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)).hasContent("a" + System.lineSeparator());
  }

  private static void assertPluginPreInstalled(PluginId expectedPluginId, IdeaPluginDescriptorImpl... descriptors) {
    var loadingResult = createPluginLoadingResult();
    loadingResult.addAll(List.of(descriptors));
    assertTrue("Plugin should be pre installed", loadingResult.shadowedBundledIds.contains(expectedPluginId));
  }

  private static void doPluginSortTest(String testDataName, boolean isBundled) throws IOException, XMLStreamException {
    PluginManagerCore.INSTANCE.getAndClearPluginLoadingErrors();
    var loadPluginResult = loadAndInitializeDescriptors(testDataName + ".xml", isBundled);
    var text = new StringBuilder();
    for (var descriptor : loadPluginResult.pluginSet.getEnabledModules()) {
      text.append(descriptor.isEnabled() ? "+ " : "  ").append(descriptor.getPluginId().getIdString());
      if (descriptor.moduleName != null) {
        text.append(" | ").append(descriptor.moduleName);
      }
      text.append('\n');
    }
    text.append("\n\n");
    for (var html : PluginManagerCore.INSTANCE.getAndClearPluginLoadingErrors()) {
      text.append(html.toString().replace("<br/>", "\n").replace("&#39;", "")).append('\n');
    }
    UsefulTestCase.assertSameLinesWithFile(new File(getTestDataPath(), testDataName + ".txt").getPath(), text.toString());
  }

  private static void assertConvertsTo(String untilBuild, String result) {
    assertEquals(result, PluginManager.convertExplicitBigNumberInUntilBuildToStar(untilBuild));
  }

  private static void assertIncompatible(String ideVersion, @Nullable String sinceBuild, @Nullable String untilBuild) {
    assertNotNull(checkCompatibility(ideVersion, sinceBuild, untilBuild));
  }

  private static @Nullable PluginLoadingError checkCompatibility(String ideVersion, @Nullable String sinceBuild, @Nullable String untilBuild) {
    IdeaPluginDescriptor mock = EasyMock.niceMock(IdeaPluginDescriptor.class);
    expect(mock.getSinceBuild()).andReturn(sinceBuild).anyTimes();
    expect(mock.getUntilBuild()).andReturn(untilBuild).anyTimes();
    expect(mock.getDependencies()).andReturn(Collections.emptyList()).anyTimes();
    replay(mock);

    return PluginManagerCore.checkBuildNumberCompatibility(mock, Objects.requireNonNull(BuildNumber.fromString(ideVersion)));
  }

  private static boolean checkCompatibility(String platformId) {
    IdeaPluginDependency platformDependencyMock = EasyMock.niceMock(IdeaPluginDependency.class);
    expect(platformDependencyMock.getPluginId()).andReturn(PluginId.getId(platformId));
    replay(platformDependencyMock);

    IdeaPluginDescriptor mock = EasyMock.niceMock(IdeaPluginDescriptor.class);
    expect(mock.getSinceBuild()).andReturn(null).anyTimes();
    expect(mock.getUntilBuild()).andReturn(null).anyTimes();
    expect(mock.getDependencies()).andReturn(Collections.singletonList(platformDependencyMock)).anyTimes();
    replay(mock);

    return PluginManagerCore.checkBuildNumberCompatibility(mock, BuildNumber.fromString("145")) == null;
  }

  private static void assertCompatible(String ideVersion, @Nullable String sinceBuild, @Nullable String untilBuild) {
    assertNull(checkCompatibility(ideVersion, sinceBuild, untilBuild));
  }

  private static PluginManagerState loadAndInitializeDescriptors(String testDataName, boolean isBundled) throws IOException, XMLStreamException {
    var file = Path.of(getTestDataPath(), testDataName);
    var buildNumber = BuildNumber.fromString("2042.42");
    var parentContext = new DescriptorListLoadingContext(Set.of(), Set.of(), Map.of(), () -> buildNumber, false, false, false, false);

    var root = XmlDomReader.readXmlAsModel(Files.newInputStream(file));
    var autoGenerateModuleDescriptor = new Ref<>(false);
    var moduleMap = new HashMap<String, XmlElement>();
    var pathResolver = new PathResolver() {
      @Override
      public boolean isFlat() {
        return false;
      }

      @Override
      public boolean loadXIncludeReference(@NotNull RawPluginDescriptor readInto,
                                           @NotNull ReadModuleContext readContext,
                                           @NotNull DataLoader dataLoader,
                                           @Nullable String base,
                                           @NotNull String relativePath) {
        throw new UnsupportedOperationException();
      }

      @Override
      public @NotNull RawPluginDescriptor resolvePath(@NotNull ReadModuleContext readContext,
                                                      @NotNull DataLoader dataLoader,
                                                      @NotNull String relativePath,
                                                      @Nullable RawPluginDescriptor readInto) {
        for (var child : root.children) {
          if (child.name.equals("config-file-idea-plugin")) {
            var url = Objects.requireNonNull(child.getAttributeValue("url"));
            if (url.endsWith("/" + relativePath)) {
              try {
                return XmlReader.readModuleDescriptor(elementAsBytes(child), readContext, this, dataLoader, null, readInto, null);
              }
              catch (XMLStreamException e) {
                throw new RuntimeException(e);
              }
            }
          }
        }
        throw new AssertionError("Unexpected: " + relativePath);
      }

      @Override
      public @NotNull RawPluginDescriptor resolveModuleFile(@NotNull ReadModuleContext readContext,
                                                                              @NotNull DataLoader dataLoader,
                                                                              @NotNull String path,
                                                                              @Nullable RawPluginDescriptor readInto) {
        if (autoGenerateModuleDescriptor.get() && path.startsWith("intellij.")) {
          var element = moduleMap.get(path);
          if (element != null) {
            try {
              return PluginBuilderKt.readModuleDescriptorForTest(elementAsBytes(element));
            }
            catch (XMLStreamException e) {
              throw new RuntimeException(e);
            }
          }

          assert readInto == null;
          // auto-generate empty descriptor
          return PluginBuilderKt.readModuleDescriptorForTest(("<idea-plugin package=\"" + path + "\"></idea-plugin>").getBytes(StandardCharsets.UTF_8));
        }
        return resolvePath(readContext, dataLoader, path, readInto);
      }
    };

    for (var element : root.children) {
      var moduleFile = element.attributes.get("moduleFile");
      if (moduleFile != null) {
        moduleMap.put(moduleFile, element);
      }
    }

    var list = new ArrayList<IdeaPluginDescriptorImpl>();
    for (var element : root.children) {
      if (!element.name.equals("idea-plugin")) {
        continue;
      }

      var url = element.getAttributeValue("url");
      Path pluginPath;
      if (url == null) {
        XmlElement id = element.getChild("id");
        if (id == null) {
          assert element.attributes.containsKey("moduleFile");
          continue;
        }

        pluginPath = Path.of(id.content.replace('.', '_') + ".xml");
        autoGenerateModuleDescriptor.set(true);
      }
      else {
        pluginPath = Path.of(Strings.trimStart(Objects.requireNonNull(url), "file://"));
      }
      var descriptor = PluginDescriptorTestKt.createFromDescriptor(
        pluginPath, isBundled, elementAsBytes(element), parentContext, pathResolver, new LocalFsDataLoader(pluginPath));
      list.add(descriptor);
      descriptor.jarFiles = List.of();
    }
    parentContext.close();
    var result = new PluginLoadingResult(false);
    result.addAll(list);
    return PluginManagerCore.INSTANCE.initializePlugins(parentContext, result, PluginManagerTest.class.getClassLoader(), false, null);
  }

  private static byte[] elementAsBytes(XmlElement child) throws XMLStreamException {
    var byteOut = new ByteArrayOutputStream();
    writeXmlElement(child, XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(byteOut, "utf-8"));
    return byteOut.toByteArray();
  }

  private static void writeXmlElement(XmlElement element, XMLStreamWriter writer) throws XMLStreamException {
    writer.writeStartElement(element.name);
    for (var entry : element.attributes.entrySet()) {
      writer.writeAttribute(entry.getKey(), entry.getValue());
    }
    if (element.content != null) {
      writer.writeCharacters(element.content);
    }
    for (var child : element.children) {
      writeXmlElement(child, writer);
    }
    writer.writeEndElement();
  }

  @SuppressWarnings("unused")
  private static String dumpDescriptors(IdeaPluginDescriptorImpl @NotNull [] descriptors) {
    // place breakpoint in PluginManagerCore#loadDescriptors before sorting
    var sb = new StringBuilder("<root>");
    Function<String, String> escape = s -> {
      return s.equals("com.intellij") || s.startsWith("com.intellij.modules.") ? s : "-" + s.replace(".", "-") + "-";
    };
    for (var d : descriptors) {
      sb.append("\n  <idea-plugin url=\"file://out/").append(d.getPluginPath().getFileName().getParent()).append("/META-INF/plugin.xml\">");
      sb.append("\n    <id>").append(escape.apply(d.getPluginId().getIdString())).append("</id>");
      sb.append("\n    <name>").append(StringUtil.escapeXmlEntities(d.getName())).append("</name>");
      for (PluginId module : d.pluginAliases) {
        sb.append("\n    <module value=\"").append(module.getIdString()).append("\"/>");
      }
      for (var dependency : d.pluginDependencies) {
        if (!dependency.isOptional()) {
          sb.append("\n    <depends>").append(escape.apply(dependency.getPluginId().getIdString())).append("</depends>");
        }
        else {
          var optionalConfigPerId = dependency.subDescriptor;
          if (optionalConfigPerId == null) {
            sb.append("\n    <depends optional=\"true\" config-file=\"???\">")
              .append(escape.apply(dependency.getPluginId().getIdString()))
              .append("</depends>");
          }
          else {
              sb.append("\n    <depends optional=\"true\" config-file=\"")
                .append(optionalConfigPerId.getPluginPath().getFileName().toString())
                .append("\">")
                .append(escape.apply(dependency.getPluginId().getIdString()))
                .append("</depends>");
          }
        }
      }
      sb.append("\n  </idea-plugin>");
    }
    sb.append("\n</root>");
    return sb.toString();
  }
}
