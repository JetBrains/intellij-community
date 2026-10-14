// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.TestOnly;

import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * IDE resource checks for a JUnit 5 test. Call them in a test of every IDE.
 * This is the replacement of {@link IdeResourcesTestCase}.
 */
@TestOnly
public final class IdeResourcesTestUtil {
  private IdeResourcesTestUtil() {
  }

  /**
   * Checks that the plugin of each registered {@link TipAndTrickBean} holds its {@code tips/<fileName>} resource.
   * Call it from any thread.
   */
  public static void checkTipFilesPresent() {
    Collection<String> errors = new TreeSet<>();
    List<TipAndTrickBean> tips = TipAndTrickBean.EP_NAME.getExtensionList();
    assertFalse(tips.isEmpty(), "no tips are registered");
    for (TipAndTrickBean tip : tips) {
      URL url = tip.getPluginDescriptor().getClassLoader().getResource("tips/" + tip.fileName);
      if (url == null) {
        errors.add(tip.fileName);
      }
    }
    assertEquals(0, errors.size(), tips.size() + " tips are checked, the following files are missing:\n" + String.join("\n", errors));
  }

  /**
   * Checks that no tip file is registered more than once as a {@link TipAndTrickBean}.
   * Call it from any thread.
   */
  public static void checkTipFilesDuplicates() {
    Collection<String> errors = new TreeSet<>();
    List<TipAndTrickBean> tips = TipAndTrickBean.EP_NAME.getExtensionList();
    assertFalse(tips.isEmpty(), "no tips are registered");
    Set<String> visited = new LinkedHashSet<>();
    for (TipAndTrickBean tip : tips) {
      if (!visited.add(tip.fileName)) {
        errors.add(tip.fileName);
      }
    }
    assertEquals(0, errors.size(), "The following tip files are registered more than once:\n" + StringUtil.join(errors, "\n"));
  }
}
