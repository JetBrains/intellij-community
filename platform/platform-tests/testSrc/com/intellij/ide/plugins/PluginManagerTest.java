// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import org.easymock.EasyMock;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    // muted failure, investigation is assigned
    doPluginSortTest("simplePluginSort", false);
  }

  @Test
  public void testUltimatePlugins() throws Exception {
    doPluginSortTest("ultimatePlugins", true);
  }

  @Test
  public void testModulePluginIdContract() {
    Path pluginsPath = Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "plugins", "withModules");
    IdeaPluginDescriptorImpl descriptorBundled = loadDescriptorInTest(pluginsPath, Collections.emptySet(), true);
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = PluginManagerCore.buildPluginIdMap(Collections.singletonList(descriptorBundled));
    PluginId moduleId = PluginId.getId("foo.bar");
    PluginId corePlugin = PluginId.getId("my.plugin");
    assertEquals(corePlugin, idMap.get(moduleId).getPluginId());
  }

  @Test
  public void testIdentifyPreInstalledPlugins() {
    Path pluginsPath = Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "plugins", "updatedBundled");
    IdeaPluginDescriptorImpl descriptorBundled = loadDescriptorInTest(pluginsPath.resolve("bundled"), Collections.emptySet(), true);
    IdeaPluginDescriptorImpl descriptorInstalled = loadDescriptorInTest(pluginsPath.resolve("updated"), Collections.emptySet(), false);
    assertEquals(descriptorBundled.getPluginId(), descriptorInstalled.getPluginId());
    PluginLoadingResult loadingResult = createPluginLoadingResult(false);
    loadingResult.add(descriptorBundled, false);
    loadingResult.add(descriptorInstalled, false);
    assertPluginPreInstalled(loadingResult, descriptorInstalled.getPluginId());
  }

  private @NotNull static PluginLoadingResult createPluginLoadingResult(boolean checkModuleDependencies) {
    BuildNumber buildNumber = BuildNumber.fromString("2042.42");
    return new PluginLoadingResult(Collections.emptyMap(), () -> buildNumber, checkModuleDependencies);
  }

  @Test
  public void testIdentifyPreInstalledPluginsInReverseOrder() {
    Path pluginsPath = Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "plugins", "updatedBundled");
    IdeaPluginDescriptorImpl descriptorBundled = loadDescriptorInTest(pluginsPath.resolve("bundled"), Collections.emptySet(), true);
    IdeaPluginDescriptorImpl descriptorInstalled = loadDescriptorInTest(pluginsPath.resolve("updated"), Collections.emptySet(), false);
    assertEquals(descriptorBundled.getPluginId(), descriptorInstalled.getPluginId());

    PluginLoadingResult resultInReverseOrder = createPluginLoadingResult(false);
    resultInReverseOrder.add(descriptorInstalled, false);
    resultInReverseOrder.add(descriptorBundled, false);
    assertPluginPreInstalled(resultInReverseOrder, descriptorInstalled.getPluginId());
  }

  @Test
  public void testSymlinkInConfigPath() throws IOException {
    assumeSymLinkCreationIsSupported();

    Path configPath = tempDir.getRoot().toPath().resolve("config-link");
    IoTestUtil.createSymbolicLink(configPath, tempDir.newDirectory("config-target").toPath());
    DisabledPluginsState.saveDisabledPlugins(configPath, "a");
    assertThat(configPath.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)).hasContent("a" + System.lineSeparator());
  }

  private static void assertPluginPreInstalled(@NotNull PluginLoadingResult loadingResult, PluginId pluginId) {
    assertTrue("Plugin should be pre installed", loadingResult.getShadowedBundledIds().contains(pluginId));
  }

  private static void doPluginSortTest(@NotNull String testDataName, boolean isBundled) throws IOException, JDOMException {
    PluginManagerCore.getAndClearPluginLoadingErrors();
    PluginManagerState loadPluginResult = loadAndInitializeDescriptors(testDataName + ".xml", isBundled);
    String actual = StringUtil.join(loadPluginResult.sortedPlugins, o -> (o.isEnabled() ? "+ " : "  ") + o.getPluginId().getIdString(), "\n") +
                    "\n\n" + PluginManagerCore.getAndClearPluginLoadingErrors().stream().map(html -> html.toString().replace("<br/>", "\n")).collect(Collectors.joining("\n"));
    UsefulTestCase.assertSameLinesWithFile(new File(getTestDataPath(), testDataName + ".txt").getPath(), actual);
  }

  private static void assertConvertsTo(String untilBuild, String result) {
    assertEquals(result, IdeaPluginDescriptorImpl.convertExplicitBigNumberInUntilBuildToStar(untilBuild));
  }

  private static void assertIncompatible(String ideVersion, String sinceBuild, String untilBuild) {
    assertNotNull(checkCompatibility(ideVersion, sinceBuild, untilBuild));
  }

  @Nullable
  private static String checkCompatibility(String ideVersion, String sinceBuild, String untilBuild) {
    IdeaPluginDescriptor mock = EasyMock.niceMock(IdeaPluginDescriptor.class);
    expect(mock.getSinceBuild()).andReturn(sinceBuild).anyTimes();
    expect(mock.getUntilBuild()).andReturn(untilBuild).anyTimes();
    replay(mock);
    PluginLoadingError error =
      PluginManagerCore.checkBuildNumberCompatibility(mock, Objects.requireNonNull(BuildNumber.fromString(ideVersion)));
    return error != null ? error.getDetailedMessage() : null;
  }

  private static void assertCompatible(String ideVersion, String sinceBuild, String untilBuild) {
    assertNull(checkCompatibility(ideVersion, sinceBuild, untilBuild));
  }

  private static @NotNull PluginManagerState loadAndInitializeDescriptors(@NotNull String testDataName, boolean isBundled)
    throws IOException, JDOMException {
    Path file = Paths.get(getTestDataPath(), testDataName);
    DescriptorListLoadingContext parentContext = new DescriptorListLoadingContext(0, Collections.emptySet(), createPluginLoadingResult(true));

    Element root = JDOMUtil.load(file, parentContext.getXmlFactory());
    BasePathResolver pathResolver = new BasePathResolver() {
      @Override
      public @NotNull Element resolvePath(@NotNull Path basePath,
                                          @NotNull String relativePath,
                                          @NotNull SafeJdomFactory jdomFactory) {
        for (Element child : root.getChildren("config-file-idea-plugin")) {
          String url = Objects.requireNonNull(child.getAttributeValue("url"));
          if (url.endsWith("/" + relativePath)) return child;
        }
        throw new AssertionError("Unexpected: " + relativePath);
      }
    };

    for (Element element : root.getChildren("idea-plugin")) {
      String url = element.getAttributeValue("url");
      Path pluginPath = Paths.get(Objects.requireNonNull(url));
      IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(pluginPath, pluginPath, isBundled);
      descriptor.readExternal(element, pathResolver, parentContext, descriptor);
      parentContext.result.add(descriptor,  /* overrideUseIfCompatible = */ false);
    }
    parentContext.close();
    parentContext.result.finishLoading();
    return PluginManagerCore.initializePlugins(parentContext, PluginManagerTest.class.getClassLoader(), /* checkEssentialPlugins = */ false);
  }

  /** @noinspection unused */
  private static String dumpDescriptors(IdeaPluginDescriptorImpl @NotNull [] descriptors) {
    // place breakpoint in PluginManagerCore#loadDescriptors before sorting
    StringBuilder sb = new StringBuilder("<root>");
    Function<String, String> escape = s -> {
      return s.equals("com.intellij") || s.startsWith("com.intellij.modules.") ? s : "-" + s.replace(".", "-") + "-";
    };
    for (IdeaPluginDescriptorImpl d : descriptors) {
      sb.append("\n  <idea-plugin url=\"file://out/").append(d.getPluginPath().getFileName().getParent()).append("/META-INF/plugin.xml\">");
      sb.append("\n    <id>").append(escape.apply(d.getPluginId().getIdString())).append("</id>");
      sb.append("\n    <name>").append(StringUtil.escapeXmlEntities(d.getName())).append("</name>");
      for (PluginId module : d.getModules()) {
        sb.append("\n    <module value=\"").append(module.getIdString()).append("\"/>");
      }
      for (PluginDependency dependency : d.getPluginDependencies()) {
        if (!dependency.isOptional) {
          sb.append("\n    <depends>").append(escape.apply(dependency.id.getIdString())).append("</depends>");
        }
        else {
          IdeaPluginDescriptorImpl optionalConfigPerId = dependency.subDescriptor;
          if (optionalConfigPerId == null) {
            sb.append("\n    <depends optional=\"true\" config-file=\"???\">").append(escape.apply(dependency.id.getIdString())).append("</depends>");
          }
          else {
              sb.append("\n    <depends optional=\"true\" config-file=\"")
                .append(optionalConfigPerId.getPluginPath().getFileName().toString()).append("\">").append(escape.apply(dependency.id.getIdString())).append("</depends>");
          }
        }
      }
      sb.append("\n  </idea-plugin>");
    }
    sb.append("\n</root>");
    return sb.toString();
  }
}
