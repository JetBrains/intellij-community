// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.NotABooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.ProjectRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

@RunsInEdt
public class TopHitProvidersTest {
  @Rule
  public final ProjectRule projectRule = new ProjectRule();
  @Rule
  public final EdtRule edtRule = new EdtRule();

  @Test
  public void testUiSettings() {
    List<String> errors = new ArrayList<>();

    List<OptionsSearchTopHitProvider> providers = ContainerUtil.concat(OptionsTopHitProvider.PROJECT_LEVEL_EP.getExtensionList(),
                                                                       ContainerUtil.mapNotNull(SearchTopHitProvider.EP_NAME.getExtensionList(), provider -> provider instanceof OptionsSearchTopHitProvider ? (OptionsSearchTopHitProvider)provider : null));
    for (OptionsSearchTopHitProvider provider : providers) {
      for (OptionDescription option : getOptions(provider)) {
        if (!(option instanceof BooleanOptionDescription)) {
          continue;
        }

        BooleanOptionDescription booleanOption = (BooleanOptionDescription)option;
        try {
          boolean enabled = booleanOption.isOptionEnabled();

          // we can't reliably restore original state for non-boolean options
          if (option instanceof NotABooleanOptionDescription) {
            continue;
          }

          booleanOption.setOptionState(!enabled);
          if (enabled == booleanOption.isOptionEnabled()) {
            errors.add("Can't set " + toString(booleanOption));
          }

          // restore
          booleanOption.setOptionState(enabled);
          if (enabled != booleanOption.isOptionEnabled()) {
            errors.add("Can't restore " + toString(booleanOption));
          }
        }
        catch (Throwable e) {
          e.printStackTrace();
          errors.add("Error while testing " + toString(booleanOption) + ": " + e.getMessage());
        }
      }
    }

    assertThat(errors).isEmpty();
  }

  @NotNull
  private Collection<OptionDescription> getOptions(@NotNull OptionsSearchTopHitProvider provider) {
    if (provider instanceof OptionsSearchTopHitProvider.ProjectLevelProvider) {
      return ((OptionsSearchTopHitProvider.ProjectLevelProvider)provider).getOptions(projectRule.getProject());
    }
    else if (provider instanceof OptionsSearchTopHitProvider.ApplicationLevelProvider) {
      return ((OptionsSearchTopHitProvider.ApplicationLevelProvider)provider).getOptions();
    }
    else {
      return Collections.emptyList();
    }
  }

  @NotNull
  private static String toString(@NotNull BooleanOptionDescription booleanOption) {
    return String.format("'%s'; id: %s; %s", booleanOption.getOption(), booleanOption.getConfigurableId(), booleanOption.getClass());
  }
}
