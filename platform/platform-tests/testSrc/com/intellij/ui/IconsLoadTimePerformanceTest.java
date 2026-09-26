// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.internal.IconsLoadTime;
import com.intellij.internal.IconsLoadTime.StatData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.ui.icons.ImageCacheKt;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.TestScaleHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * Estimates SVG icon average load time.
 *
 * @author tav
 */
@PerformanceUnitTest
public class IconsLoadTimePerformanceTest {
  private static final Logger LOG = Logger.getInstance(IconsLoadTimePerformanceTest.class);
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
  public void test() throws ClassNotFoundException {
    TestScaleHelper.setSystemProperty("idea.ui.icons.svg.disk.cache", "false");
    loadIcons("svg icons load, no disk cache");

    TestScaleHelper.setSystemProperty("idea.ui.icons.svg.disk.cache", "true");
    loadIcons("svg icons load, disk cache");
  }

  public void loadIcons(String launchName) throws ClassNotFoundException {
    // force static init
    assertNotNull(Class.forName(IconsLoadTime.class.getName()));

    Benchmark.newBenchmark(launchName, () -> {
      try (BufferedReader br = Files.newBufferedReader(Paths.get(ICONS_LIST_PATH))) {
        String iconPath;
        while ((iconPath = br.readLine()) != null) {
          URL url = new File(PlatformTestUtil.getCommunityPath() + "/" + iconPath).toURI().toURL();
          // do not use global cache
          //noinspection KotlinInternalInJava
          ImageCacheKt.loadImage(url.toString(), null, null, ScaleContext.create(), false, null, Collections.emptyList(), false, false);
        }
      }
    }).attempts(1).start();

    StatData svgData = IconsLoadTime.getStatData(false, true);

    assumeTrue("no SVG load statistics gathered", svgData != null);
    LOG.debug(String.valueOf(svgData));

    assumeTrue("too few icons loaded: " + svgData.count + "; expecting > " + SVG_ICON_QUORUM_COUNT,
               svgData.count >= SVG_ICON_QUORUM_COUNT);
  }
}
