// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.openapi.util.text.StringUtil;

import java.net.URL;
import java.util.*;

/**
 * This test-case should be extended in every IDE.
 *
 * @author gregsh
 */
public abstract class IdeResourcesTestCase extends LightPlatformTestCase {
  public void testTipFilesPresent() {
    Collection<String> errors = new TreeSet<>();
    List<TipAndTrickBean> tips = TipAndTrickBean.EP_NAME.getExtensionList();
    assertNotEmpty(tips);
    for (TipAndTrickBean tip : tips) {
      URL url = tip.getPluginDescriptor().getClassLoader().getResource("tips/" + tip.fileName);
      if (url == null) {
        errors.add(tip.fileName);
      }
    }
    assertEquals(tips.size() + " tips are checked, the following files are missing:\n" + String.join("\n", errors), 0, errors.size());
  }

  public void testTipFilesDuplicates() {
    Collection<String> errors = new TreeSet<>();
    TipAndTrickBean[] tips = TipAndTrickBean.EP_NAME.getExtensions();
    assertNotEmpty(Arrays.asList(tips));
    Set<String> visited = new LinkedHashSet<>();
    for (TipAndTrickBean tip : tips) {
      if (!visited.add(tip.fileName)) {
        errors.add(tip.fileName);
      }
    }
    assertEquals("The following tip files are registered more than once:\n" + StringUtil.join(errors, "\n"), 0, errors.size());
  }
}
