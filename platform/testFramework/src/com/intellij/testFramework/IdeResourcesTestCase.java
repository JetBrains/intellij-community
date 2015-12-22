/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.featureStatistics.FeatureDescriptor;
import com.intellij.featureStatistics.ProductivityFeaturesRegistry;
import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ResourceUtil;
import com.intellij.util.containers.ContainerUtil;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * This test-case should be extended in every IDE.
 *
 * @author gregsh
 */
public abstract class IdeResourcesTestCase extends PlatformTestCase {

  public void testFeatureTipsRegistered() {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    Set<String> ids = registry.getFeatureIds();
    assertNotEmpty(ids);

    Collection<String> errors = ContainerUtil.newTreeSet();
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
    Collection<String> errors = ContainerUtil.newTreeSet();
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
    Collection<String> errors = ContainerUtil.newTreeSet();
    TipAndTrickBean[] tips = TipAndTrickBean.EP_NAME.getExtensions();
    assertNotEmpty(Arrays.asList(tips));
    Set<String> visited = ContainerUtil.newLinkedHashSet();
    for (TipAndTrickBean tip : tips) {
      if (!visited.add(tip.fileName)) {
        errors.add(tip.fileName);
      }
    }
    assertEquals("The following tip files are registered more than once:\n" + StringUtil.join(errors, "\n"), 0, errors.size());
  }
}
