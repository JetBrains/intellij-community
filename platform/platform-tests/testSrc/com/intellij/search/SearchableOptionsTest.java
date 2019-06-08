// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.search;

import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.ide.ui.search.ConfigurableHit;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.testFramework.LightPlatformTestCase;

import javax.swing.event.DocumentEvent;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author anna
 */
public class SearchableOptionsTest extends LightPlatformTestCase {
  public void testFindCodeTemplates() {
    List<ConfigurableGroup> groups = Collections.singletonList(ConfigurableExtensionPointUtil.getConfigurableGroup(getProject(), false));
    ConfigurableHit configurables = SearchableOptionsRegistrar.getInstance().getConfigurables(groups, DocumentEvent.EventType.INSERT, null, "method", getProject());
    Set<Configurable> configurableSet = configurables.getAll();
    for (Configurable configurable : configurableSet) {
      if (configurable.getDisplayName().equals(new AllFileTemplatesConfigurable(getProject()).getDisplayName())) {
        return;
      }
    }
    fail("File Templates are not found");
  }
}
