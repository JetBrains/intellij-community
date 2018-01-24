/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util;

import com.intellij.internal.IconsLoadTime;
import com.intellij.internal.IconsLoadTime.StatData;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.testFramework.PlatformTestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;

import static com.intellij.util.ImageLoader.ImageDesc.Type;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

/**
 * Estimates SVG icon average load time.
 *
 * @author tav
 */
public class IconsLoadTimeTest {
  private static final int SVG_ICON_AVERAGE_LOAD_TIME_ESTIMATE = 10; // ms
  private static final int SVG_ICON_QUORUM_COUNT = 50;

  // a list of icons for which we have SVG versions
  private static final String ICONS_LIST_PATH = PlatformTestUtil.getPlatformTestDataPath() + "icons/icons_list.txt";

  private static boolean internalProp;
  private static boolean svgProp;

  @Before
  public void setState() {
    internalProp = "true".equalsIgnoreCase(System.getProperty("idea.is.internal"));
    System.setProperty("idea.is.internal", "true");
    RegistryValue rv = Registry.get("ide.svg.icon");
    svgProp = rv.asBoolean();
    rv.setValue(true);
  }

  @Test
  public void loadIcons() throws ClassNotFoundException, IOException {
    assertNotNull(Class.forName(IconsLoadTime.class.getName())); // force static init

    try (BufferedReader br = new BufferedReader(new FileReader(new File(ICONS_LIST_PATH)))) {
      String iconPath;
      while ((iconPath = br.readLine()) != null) {
        ImageLoader.loadFromUrl(new File(PlatformTestUtil.getCommunityPath() + "/" + iconPath).toURI().toURL());
      }
    }
    StatData svgData = IconsLoadTime.getStatData(false, Type.SVG);
    assertNotNull("failed to gather SVG load statistics", svgData);
    System.out.println(svgData);

    assertEquals("too few icons loaded: " + svgData.count + "; expecting > " + SVG_ICON_QUORUM_COUNT,
                 true, svgData.count >= SVG_ICON_QUORUM_COUNT);

    assertEquals("SVG icon load time raised to " + String.format("%.02fms", svgData.averageTime) +
                 ", exceeding max " + SVG_ICON_AVERAGE_LOAD_TIME_ESTIMATE + "ms",
                 true, svgData.averageTime <= SVG_ICON_AVERAGE_LOAD_TIME_ESTIMATE);
  }

  @After
  public void restoreState() {
    System.setProperty("idea.is.internal", internalProp ? "true" : "false");
    Registry.get("ide.svg.icon").setValue(svgProp);
  }
}
