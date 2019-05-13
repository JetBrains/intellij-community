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
package com.intellij.search;

import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.ide.ui.search.ConfigurableHit;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.testFramework.LightPlatformTestCase;

import javax.swing.event.DocumentEvent;
import java.util.Set;

/**
 * @author anna
 */
public class SearchableOptionsTest extends LightPlatformTestCase {
  public void testFindCodeTemplates() {
    ConfigurableGroup[] groups = ShowSettingsUtilImpl.getConfigurableGroups(getProject(), false);
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
