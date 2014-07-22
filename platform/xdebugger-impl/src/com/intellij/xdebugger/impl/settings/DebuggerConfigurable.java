/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Eugene Belyaev & Eugene Zhuravlev
 */
public class DebuggerConfigurable implements SearchableConfigurable.Parent {
  public static final String DISPLAY_NAME = XDebuggerBundle.message("debugger.configurable.display.name");

  static final Configurable[] EMPTY_CONFIGURABLES = new Configurable[0];

  private Configurable myRootConfigurable;
  private Configurable[] myChildren;

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public String getHelpTopic() {
    return myRootConfigurable != null ? myRootConfigurable.getHelpTopic() : null;
  }

  @Override
  public Configurable[] getConfigurables() {
    compute();

    if (myChildren.length == 0 && myRootConfigurable instanceof SearchableConfigurable.Parent) {
      return ((Parent)myRootConfigurable).getConfigurables();
    }
    else {
      return myChildren;
    }
  }

  private void compute() {
    if (myChildren != null) {
      return;
    }

    List<DebuggerSettingsPanelProvider> providers = DebuggerConfigurableProvider.getSortedProviders();

    List<Configurable> configurables = new SmartList<Configurable>();
    configurables.add(new DataViewsConfigurable());

    List<Configurable> steppingConfigurables = DebuggerConfigurableProvider.getConfigurables(XDebuggerSettings.Category.STEPPING, providers);
    if (!steppingConfigurables.isEmpty()) {
      configurables.add(new SteppingConfigurable(steppingConfigurables));
    }

    Configurable rootConfigurable = computeRootConfigurable(providers, configurables);

    if (configurables.isEmpty() && rootConfigurable == null) {
      myChildren = EMPTY_CONFIGURABLES;
    }
    else if (rootConfigurable == null && configurables.size() == 1) {
      myRootConfigurable = configurables.get(0);
      myChildren = EMPTY_CONFIGURABLES;
    }
    else {
      myChildren = configurables.toArray(new Configurable[configurables.size()]);
      myRootConfigurable = rootConfigurable;
    }
  }

  @Nullable
  private static Configurable computeRootConfigurable(@NotNull List<DebuggerSettingsPanelProvider> providers, @NotNull List<Configurable> configurables) {
    Configurable deprecatedRootConfigurable = null;
    for (DebuggerSettingsPanelProvider provider : providers) {
      configurables.addAll(provider.getConfigurables());
      @SuppressWarnings("deprecation")
      Configurable providerRootConfigurable = provider.getRootConfigurable();
      if (providerRootConfigurable != null) {
        if (deprecatedRootConfigurable == null) {
          deprecatedRootConfigurable = providerRootConfigurable;
        }
        else {
          configurables.add(providerRootConfigurable);
        }
      }
    }

    List<Configurable> rootConfigurables = DebuggerConfigurableProvider.getConfigurables(XDebuggerSettings.Category.ROOT, providers);
    if (rootConfigurables.isEmpty()) {
      return deprecatedRootConfigurable;
    }
    else {
      Configurable[] mergedRootConfigurables = new Configurable[rootConfigurables.size() + (deprecatedRootConfigurable == null ? 0 : 1)];
      rootConfigurables.toArray(mergedRootConfigurables);
      if (deprecatedRootConfigurable != null) {
        mergedRootConfigurables[rootConfigurables.size()] = deprecatedRootConfigurable;
      }

      // move unnamed to top
      Arrays.sort(mergedRootConfigurables, new Comparator<Configurable>() {
        @Override
        public int compare(Configurable o1, Configurable o2) {
          boolean c1e = StringUtil.isEmpty(o1.getDisplayName());
          return c1e == StringUtil.isEmpty(o2.getDisplayName()) ? 0 : (c1e ? -1 : 1);
        }
      });

      return new MergedCompositeConfigurable(mergedRootConfigurables) {
        @NotNull
        @Override
        public String getId() {
          throw new UnsupportedOperationException();
        }

        @Nls
        @Override
        public String getDisplayName() {
          throw new UnsupportedOperationException();
        }
      };
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      support.getSettingsPanelProvider().apply();
    }
    if (myRootConfigurable != null) {
      myRootConfigurable.apply();
    }
  }

  @Override
  public boolean hasOwnContent() {
    compute();
    return myRootConfigurable != null;
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Override
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Override
  public JComponent createComponent() {
    compute();
    return myRootConfigurable != null ? myRootConfigurable.createComponent() : null;
  }

  @Override
  public boolean isModified() {
    return myRootConfigurable != null && myRootConfigurable.isModified();
  }

  @Override
  public void reset() {
    if (myRootConfigurable != null) {
      myRootConfigurable.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    if (myRootConfigurable != null) {
      myRootConfigurable.disposeUIResources();
    }
  }

  @Override
  @NotNull
  @NonNls
  public String getId() {
    return "project.propDebugger";
  }
}
