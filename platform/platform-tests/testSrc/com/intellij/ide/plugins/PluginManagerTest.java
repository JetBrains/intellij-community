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

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.JDOMXIncluder;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PluginManagerTest {

  private static String getTestDataPath() {
    return PathManagerEx.getTestDataPath() + "/ide/plugins/sort";
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
    doPluginSortTest("simplePluginSort");
  }

  @Test
  public void testUltimatePlugins() throws Exception {
    doPluginSortTest("ultimatePlugins");
  }

  private static void doPluginSortTest(@NotNull String testDataName) throws IOException, JDOMException {
    PluginManagerCore.ourPluginError = null;
    List<IdeaPluginDescriptorImpl> descriptors = loadDescriptors(testDataName + ".xml");
    IdeaPluginDescriptorImpl[] sorted = PluginManagerCore.initializePlugins(
      descriptors.toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY), PluginManagerTest.class.getClassLoader(), null);
    String actual = StringUtil.join(sorted, o -> (o.isEnabled() ? "+ " : "  ") + o.getPluginId().getIdString(), "\n") +
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

  private static List<IdeaPluginDescriptorImpl> loadDescriptors(@NotNull String testDataName) throws IOException, JDOMException {
    File file = new File(getTestDataPath(), testDataName);
    List<IdeaPluginDescriptorImpl> result = new ArrayList<>();
    LoadDescriptorsContext context = new LoadDescriptorsContext(false);
    Element root = JDOMUtil.load(file, context.getXmlFactory());
    for (Element element : root.getChildren("idea-plugin")) {
      String url = element.getAttributeValue("url");
      IdeaPluginDescriptorImpl d = new IdeaPluginDescriptorImpl(new File(url), false);
      d.readExternal(element, new URL(url), JDOMXIncluder.DEFAULT_PATH_RESOLVER,
                     context.getXmlFactory().stringInterner(), false);
      result.add(d);
    }
    Collections.sort(result, (o1, o2) -> Comparing.compare(String.valueOf(o1.getPluginId()),
                                                           String.valueOf(o2.getPluginId())));
    return result;
  }

  /** @noinspection unused */
  private static String dumpDescriptors(@NotNull IdeaPluginDescriptorImpl[] descriptors) {
    StringBuilder sb = new StringBuilder("<root>");
    for (IdeaPluginDescriptorImpl d : descriptors) {
      sb.append("\n  <idea-plugin url=\"file://out/").append(d.getPath().getName()).append("/META-INF/plugin.xml\">");
      sb.append("\n    <id>").append(d.getPluginId()).append("</id>");
      sb.append("\n    <name>").append(StringUtil.escapeXmlEntities(d.getName())).append("</name>");
      for (String module : d.getModules()) {
        sb.append("\n    <module value=\"").append(module).append("\"/>");
      }
      PluginId[] optIds = d.getOptionalDependentPluginIds();
      Map<PluginId, List<IdeaPluginDescriptorImpl>> optMap = d.getOptionalDescriptors();
      for (PluginId depId : d.getDependentPluginIds()) {
        if (ArrayUtil.indexOf(optIds, depId) == -1) {
          sb.append("\n    <depends>").append(depId).append("</depends>");
        }
        else {
          List<IdeaPluginDescriptorImpl> opt = optMap != null ? optMap.get(depId) : null;
          if (opt == null || opt.isEmpty()) {
            sb.append("\n    <depends optional=\"true\" config-file=\"???\">").append(depId).append("</depends>");
          }
          else {
            for (IdeaPluginDescriptorImpl dd : opt) {
              sb.append("\n    <depends optional=\"true\" config-file=\"")
                .append(dd.getPath().getName()).append("\">").append(depId).append("</depends>");
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