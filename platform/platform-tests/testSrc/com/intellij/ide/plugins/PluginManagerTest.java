// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.*;

public class PluginManagerTest {
  private static String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "plugins/sort";
  }

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
    doPluginSortTest("simplePluginSort", false);
  }

  @Test
  public void testUltimatePlugins() throws Exception {
    doPluginSortTest("ultimatePlugins", true);
  }

  private static void doPluginSortTest(@NotNull String testDataName, boolean isBundled) throws IOException, JDOMException {
    PluginManagerCore.ourPluginError = null;
    PluginLoadingResult loadPluginResult = loadDescriptors(testDataName + ".xml", isBundled);
    PluginManagerCore.initializePlugins(loadPluginResult, PluginManagerTest.class.getClassLoader(), /* checkEssentialPlugins = */ false);
    String actual = StringUtil.join(loadPluginResult.getSortedPlugins(), o -> (o.isEnabled() ? "+ " : "  ") + o.getPluginId().getIdString(), "\n") +
                    "\n\n" + StringUtil.notNullize(PluginManagerCore.ourPluginError).replace("<p/>", "\n");
    PluginManagerCore.ourPluginError = null;
    UsefulTestCase.assertSameLinesWithFile(new File(getTestDataPath(), testDataName + ".txt").getPath(), actual);
  }

  private static void assertConvertsTo(String untilBuild, String result) {
    assertEquals(result, IdeaPluginDescriptorImpl.convertExplicitBigNumberInUntilBuildToStar(untilBuild));
  }

  private static void assertIncompatible(String ideVersion, String sinceBuild, String untilBuild) {
    assertNotNull(PluginManagerCore.isIncompatible(BuildNumber.fromString(ideVersion), sinceBuild, untilBuild));
  }

  private static void assertCompatible(String ideVersion, String sinceBuild, String untilBuild) {
    assertNull(PluginManagerCore.isIncompatible(BuildNumber.fromString(ideVersion), sinceBuild, untilBuild));
  }

  @NotNull
  private static PluginLoadingResult loadDescriptors(@NotNull String testDataName, boolean isBundled)
    throws IOException, JDOMException {
    Path file = Paths.get(getTestDataPath(), testDataName);
    PluginLoadingResult result = new PluginLoadingResult(Collections.emptyMap());

    DescriptorListLoadingContext parentContext = new DescriptorListLoadingContext(/* doesn't matter */ false, Collections.emptySet());
    DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, isBundled, /* doesn't matter */ false,
                                                                    PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER);

    Element root = JDOMUtil.load(file, context.parentContext.getXmlFactory());

    for (Element element : root.getChildren("idea-plugin")) {
      String url = element.getAttributeValue("url");
      IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(Paths.get(url), isBundled);
      context.readDescriptor(descriptor, element, Paths.get(url), context.pathResolver);
      result.add(descriptor, false);
    }
    result.finishLoading();
    return result;
  }

  /** @noinspection unused */
  private static String dumpDescriptors(@NotNull IdeaPluginDescriptorImpl[] descriptors) {
    // place breakpoint in PluginManagerCore#loadDescriptors before sorting
    StringBuilder sb = new StringBuilder("<root>");
    Function<String, String> escape = s ->
      s.equals("com.intellij") || s.startsWith("com.intellij.modules.") ? s : "-" + s.replace(".", "-")+ "-";
    for (IdeaPluginDescriptorImpl d : descriptors) {
      sb.append("\n  <idea-plugin url=\"file://out/").append(d.getPath().getName()).append("/META-INF/plugin.xml\">");
      sb.append("\n    <id>").append(escape.apply(d.getPluginId().getIdString())).append("</id>");
      sb.append("\n    <name>").append(StringUtil.escapeXmlEntities(d.getName())).append("</name>");
      for (PluginId module : d.getModules()) {
        sb.append("\n    <module value=\"").append(module.getIdString()).append("\"/>");
      }
      PluginId[] optIds = d.getOptionalDependentPluginIds();
      Map<PluginId, List<Map.Entry<String, IdeaPluginDescriptorImpl>>> optionalConfigs = d.optionalConfigs;
      for (PluginId depId : d.getDependentPluginIds()) {
        if (ArrayUtil.indexOf(optIds, depId) == -1) {
          sb.append("\n    <depends>").append(escape.apply(depId.getIdString())).append("</depends>");
        }
        else {
          List<Map.Entry<String, IdeaPluginDescriptorImpl>> optionalConfigPerId = optionalConfigs == null ? null : optionalConfigs.get(depId);
          if (optionalConfigPerId == null || optionalConfigPerId.isEmpty()) {
            sb.append("\n    <depends optional=\"true\" config-file=\"???\">").append(escape.apply(depId.getIdString())).append("</depends>");
          }
          else {
            for (Map.Entry<String, IdeaPluginDescriptorImpl> entry : optionalConfigPerId) {
              IdeaPluginDescriptorImpl descriptor = entry.getValue();
              if (descriptor == null) {
                continue;
              }

              sb.append("\n    <depends optional=\"true\" config-file=\"")
                .append(descriptor.getPath().getName()).append("\">").append(escape.apply(depId.getIdString())).append("</depends>");
            }
          }
        }
      }
      sb.append("\n  </idea-plugin>");
    }
    sb.append("\n</root>");
    return sb.toString();
  }
}