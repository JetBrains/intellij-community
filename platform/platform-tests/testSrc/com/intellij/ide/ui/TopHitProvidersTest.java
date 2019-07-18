/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.application.options.editor.EditorTabsOptionsModelKt;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Konstantin Bulenkov
 */
public class TopHitProvidersTest extends LightPlatformTestCase {
  private static final List<String> BLACKLIST = ContainerUtil.newArrayList(EditorTabsOptionsModelKt.ID);

  public void testUiSettings() {
    List<String> errors = new ArrayList<>();

    List<OptionsSearchTopHitProvider> providers = getProviders();
    for (OptionsSearchTopHitProvider provider : providers) {
      for (OptionDescription option : getOptions(provider)) {
        if (option instanceof BooleanOptionDescription) {
          if (BLACKLIST.contains(option.getConfigurableId())) continue;

          try {
            BooleanOptionDescription booleanOption = (BooleanOptionDescription)option;
            boolean enabled = booleanOption.isOptionEnabled();
            booleanOption.setOptionState(!enabled);
            if (enabled == booleanOption.isOptionEnabled()) errors.add("Can't set " + toString(booleanOption));
            booleanOption.setOptionState(enabled); //restore
            if (enabled != booleanOption.isOptionEnabled()) errors.add("Can't restore " + toString(booleanOption));
          }
          catch (Throwable e) {
            e.printStackTrace();
            errors.add(e.getMessage());
          }
        }
      }
    }

    assertEmpty(errors);
  }

  private static List<OptionsSearchTopHitProvider> getProviders() {
    return Stream.concat(OptionsTopHitProvider.PROJECT_LEVEL_EP.getExtensionList().stream(),
                         StreamEx.of(SearchTopHitProvider.EP_NAME.getExtensionList()).select(OptionsSearchTopHitProvider.class))
      .collect(Collectors.toList());
  }

  private Collection<OptionDescription> getOptions(@NotNull OptionsSearchTopHitProvider provider) {
    if (provider instanceof OptionsSearchTopHitProvider.ProjectLevelProvider) {
      return ((OptionsSearchTopHitProvider.ProjectLevelProvider)provider).getOptions(getProject());
    }
    else if (provider instanceof OptionsSearchTopHitProvider.ApplicationLevelProvider) {
      return ((OptionsSearchTopHitProvider.ApplicationLevelProvider)provider).getOptions();
    }
    else if (provider instanceof OptionsTopHitProvider) {
      return ((OptionsTopHitProvider)provider).getOptions(getProject());
    }
    return Collections.emptyList();
  }

  @NotNull
  private static String toString(@NotNull BooleanOptionDescription booleanOption) {
    return String.format("'%s'; id: %s; %s", booleanOption.getOption(), booleanOption.getConfigurableId(), booleanOption.getClass());
  }
}
