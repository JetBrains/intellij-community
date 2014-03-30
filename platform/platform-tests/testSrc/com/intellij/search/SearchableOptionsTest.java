/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.search;

import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.ide.ui.search.ConfigurableHit;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ex.IdeConfigurablesGroup;
import com.intellij.testFramework.LightPlatformLangTestCase;

import javax.swing.event.DocumentEvent;
import java.util.Set;

/**
 * User: anna
 */
public class SearchableOptionsTest extends LightPlatformLangTestCase {
  public void testFindCodeTemplates() throws Exception {
    final ConfigurableHit configurables =
      SearchableOptionsRegistrar.getInstance().getConfigurables(new ConfigurableGroup[]{new IdeConfigurablesGroup()}, DocumentEvent.EventType.INSERT, null, "method", getProject());
    final Set<Configurable> configurableSet = configurables.getAll();
    for (Configurable configurable : configurableSet) {
      if (configurable.getDisplayName().equals(new AllFileTemplatesConfigurable().getDisplayName())) {
        return;
      }
    }
    fail("File Templates are not found");
  }
}
