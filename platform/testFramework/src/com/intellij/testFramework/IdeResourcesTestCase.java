// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.featureStatistics.FeatureDescriptor;
import com.intellij.featureStatistics.ProductivityFeaturesRegistry;
import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ResourceUtil;

import java.net.URL;
import java.util.*;

/**
 * This test-case should be extended in every IDE.
 *
 * @author gregsh
 */
public abstract class IdeResourcesTestCase extends LightPlatformTestCase {

  public void testFeatureTipsRegistered() {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    Set<String> ids = registry.getFeatureIds();
    assertNotEmpty(ids);

    Collection<String> errors = new TreeSet<>();
    for (String id : ids) {
      FeatureDescriptor descriptor = registry.getFeatureDescriptor(id);
      TipAndTrickBean tip = TipAndTrickBean.findByFileName(descriptor.getTipFileName());
      if (tip == null) {
        errors.add("<tipAndTrick file=\"" + descriptor.getTipFileName() + "\" feature-id=\"" + id + "\"/>");
      }
    }
    assertEquals("Register the following extensions:\n" + StringUtil.join(errors, "\n"), 0, errors.size());
  }

  public void testTipFilesPresent() {
    Collection<String> errors = new TreeSet<>();
    TipAndTrickBean[] tips = TipAndTrickBean.EP_NAME.getExtensions();
    assertNotEmpty(Arrays.asList(tips));
    for (TipAndTrickBean tip : tips) {
      URL url = ResourceUtil.getResource(tip.getPluginDescriptor().getPluginClassLoader(), "/tips/", tip.fileName);
      if (url == null) {
        errors.add(tip.fileName);
      }
    }
    assertEquals(tips.length + " tips are checked, the following files are missing:\n" + StringUtil.join(errors, "\n"), 0, errors.size());
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
