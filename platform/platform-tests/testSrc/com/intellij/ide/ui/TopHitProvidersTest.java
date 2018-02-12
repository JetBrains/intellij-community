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

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.testFramework.PlatformTestCase;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
public class TopHitProvidersTest extends PlatformTestCase {
  public void testUiSettings() {
    List<OptionsTopHitProvider> providers = getProvider(AppearanceOptionsTopHitProvider.ID);
    for (OptionsTopHitProvider provider: providers) {
      for (OptionDescription option : provider.getOptions(null)) {
        if (option instanceof BooleanOptionDescription) {
          BooleanOptionDescription booleanOption = (BooleanOptionDescription)option;
          boolean enabled = booleanOption.isOptionEnabled();
          booleanOption.setOptionState(!enabled);
          assert enabled != booleanOption.isOptionEnabled() : "Can't set " + booleanOption.getOption();
          booleanOption.setOptionState(!enabled); //restore
        }
      }
    }
  }

  private static List<OptionsTopHitProvider> getProvider(String id) {
    return Arrays.stream(SearchTopHitProvider.EP_NAME.getExtensions())
      .filter(p -> p instanceof OptionsTopHitProvider && ((OptionsTopHitProvider)p).getId().equals(id))
      .map(p -> (OptionsTopHitProvider)p)
      .collect(Collectors.toList());
  }
}
