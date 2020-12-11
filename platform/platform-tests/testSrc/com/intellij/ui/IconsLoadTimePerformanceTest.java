// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.idea.HardwareAgentRequired;
import com.intellij.internal.IconsLoadTime;
import com.intellij.internal.IconsLoadTime.StatData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.TestScaleHelper;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.StartupUiUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.intellij.testFramework.PlatformTestUtil.assertTiming;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * Estimates SVG icon average load time.
 *
 * @author tav
 */
@HardwareAgentRequired
public class IconsLoadTimePerformanceTest {
  private static final Logger LOG = Logger.getInstance(IconsLoadTimePerformanceTest.class);
  private static final int SVG_ICON_AVERAGE_LOAD_TIME_EXPECTED_NO_CACHE = 100; // ms
  private static final int SVG_ICON_AVERAGE_LOAD_TIME_EXPECTED_CACHE = 50; // ms
  private static final int SVG_ICON_QUORUM_COUNT = 50;

  // a list of icons for which we have SVG versions
  private static final String ICONS_LIST_PATH = PlatformTestUtil.getPlatformTestDataPath() + "icons/icons_list.txt";

  @Before
  public void setState() {
    TestScaleHelper.setSystemProperty("idea.measure.icon.load.time", "true");
    TestScaleHelper.setSystemProperty("idea.ui.icons.svg.disk.cache", "false");
  }

  @After
  public void restoreState() {
    TestScaleHelper.restoreSystemProperties();
    TestScaleHelper.restoreRegistryProperties();
  }

  @Test
  public void test() throws IOException, ClassNotFoundException {
    TestScaleHelper.setSystemProperty("idea.ui.icons.svg.disk.cache", "false");
    loadIcons(SVG_ICON_AVERAGE_LOAD_TIME_EXPECTED_NO_CACHE);

    TestScaleHelper.setSystemProperty("idea.ui.icons.svg.disk.cache", "true");
    loadIcons(SVG_ICON_AVERAGE_LOAD_TIME_EXPECTED_CACHE);
  }

  public void loadIcons(int expectedTime) throws ClassNotFoundException, IOException {
    // force static init
    assertNotNull(Class.forName(IconsLoadTime.class.getName()));

    try (BufferedReader br = Files.newBufferedReader(Paths.get(ICONS_LIST_PATH))) {
      String iconPath;
      while ((iconPath = br.readLine()) != null) {
        URL url = new File(PlatformTestUtil.getCommunityPath() + "/" + iconPath).toURI().toURL();
        /* do not use global cache */
        int flags = ImageLoader.USE_SVG | ImageLoader.ALLOW_FLOAT_SCALING;
        if (StartupUiUtil.isUnderDarcula()) {
          flags |= ImageLoader.USE_DARK;
        }
        ImageLoader.loadFromUrl(url.toString(), null, flags, ScaleContext.create());
      }
    }
    StatData svgData = IconsLoadTime.getStatData(false, true);

    assumeTrue("no SVG load statistics gathered", svgData != null);
    LOG.debug(String.valueOf(svgData));

    assumeTrue("too few icons loaded: " + svgData.count + "; expecting > " + SVG_ICON_QUORUM_COUNT,
               svgData.count >= SVG_ICON_QUORUM_COUNT);

    assertTiming("SVG icon load time raised to " + String.format("%.02fms", svgData.averageTime),
                 expectedTime, (int)svgData.averageTime);
  }
}
