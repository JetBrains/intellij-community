// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DebuggerConfigurable implements SearchableConfigurable.Parent {
  static final Configurable[] EMPTY_CONFIGURABLES = new Configurable[0];
  private static final DebuggerSettingsCategory[] MERGED_CATEGORIES = {DebuggerSettingsCategory.STEPPING, DebuggerSettingsCategory.HOTSWAP};

  private Configurable myRootConfigurable;
  private Configurable[] myChildren;

  @Override
  public String getDisplayName() {
    return getDisplayNameText();
  }

  @Override
  public String getHelpTopic() {
    return "reference.idesettings.debugger";
  }

  @Override
  public Configurable @NotNull [] getConfigurables() {
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

    List<Configurable> configurables = new SmartList<>();
    configurables.add(new DataViewsConfigurable());

    List<DebuggerConfigurableProvider> providers = DebuggerConfigurableProvider.EXTENSION_POINT.getExtensionList();
    computeMergedConfigurables(providers, configurables);

    for (DebuggerConfigurableProvider provider : providers) {
      configurables.addAll(provider.getConfigurables(DebuggerSettingsCategory.ROOT));
    }

    MergedCompositeConfigurable mergedGeneralConfigurable = computeGeneralConfigurables(providers);
    if (configurables.isEmpty() && mergedGeneralConfigurable == null) {
      myRootConfigurable = null;
      myChildren = EMPTY_CONFIGURABLES;
    }
    else if (configurables.size() == 1) {
      Configurable firstConfigurable = configurables.get(0);
      if (mergedGeneralConfigurable == null) {
        myRootConfigurable = firstConfigurable;
        myChildren = EMPTY_CONFIGURABLES;
      }
      else {
        Configurable[] mergedArray = ArrayUtil.append(mergedGeneralConfigurable.children, firstConfigurable);
        myRootConfigurable = new MergedCompositeConfigurable(getId(), getDisplayName(), null, mergedArray);
        myChildren = firstConfigurable instanceof SearchableConfigurable.Parent ? ((Parent)firstConfigurable).getConfigurables() : EMPTY_CONFIGURABLES;
      }
    }
    else {
      myChildren = configurables.toArray(new Configurable[0]);
      myRootConfigurable = mergedGeneralConfigurable;
    }
  }

  private static void computeMergedConfigurables(List<DebuggerConfigurableProvider> providers, @NotNull List<? super Configurable> result) {
    for (DebuggerSettingsCategory category : MERGED_CATEGORIES) {
      List<Configurable> configurables = getConfigurables(category, providers);
      if (!configurables.isEmpty()) {
        String id = StringUtil.toLowerCase(category.name());
        result.add(new MergedCompositeConfigurable("debugger." + id, XDebuggerBundle.message("debugger." + id + ".display.name"),
                                                   getDefaultCategoryHelpTopic(category), configurables.toArray(new Configurable[0])));
      }
    }
  }

  private @Nullable MergedCompositeConfigurable computeGeneralConfigurables(List<DebuggerConfigurableProvider> providers) {
    List<Configurable> rootConfigurables = getConfigurables(DebuggerSettingsCategory.GENERAL, providers);
    if (rootConfigurables.isEmpty()) {
      return null;
    }

    Configurable[] mergedRootConfigurables = rootConfigurables.toArray(new Configurable[0]);
    // move unnamed to top
    Arrays.sort(mergedRootConfigurables, (o1, o2) -> {
      boolean c1e = StringUtil.isEmpty(o1.getDisplayName());
      return c1e == StringUtil.isEmpty(o2.getDisplayName()) ? 0 : (c1e ? -1 : 1);
    });
    return new MergedCompositeConfigurable(getId(), getDisplayName(), null, mergedRootConfigurables);
  }

  @Override
  public void apply() throws ConfigurationException {
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
  public @NotNull @NonNls String getId() {
    return "project.propDebugger";
  }

  static @Unmodifiable @NotNull List<Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category) {
    return getConfigurables(category, DebuggerConfigurableProvider.EXTENSION_POINT.getExtensionList());
  }

  private static @Unmodifiable @NotNull List<Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category,
                                                                            List<DebuggerConfigurableProvider> providers) {
    List<Configurable> configurables = null;
    for (DebuggerConfigurableProvider provider : providers) {
      Collection<? extends Configurable> providerConfigurables = provider.getConfigurables(category);
      if (!providerConfigurables.isEmpty()) {
        if (configurables == null) {
          configurables = new SmartList<>();
        }
        configurables.addAll(providerConfigurables);
      }
    }
    return ContainerUtil.notNullize(configurables);
  }

  private static String getDefaultCategoryHelpTopic(DebuggerSettingsCategory category) {
    return switch (category) {
      case STEPPING -> "reference.idesettings.debugger.stepping";
      case HOTSWAP -> "reference.idesettings.debugger.hotswap";
      default -> null;
    };
  }

  public static @Nls String getDisplayNameText() {
    return XDebuggerBundle.message("debugger.configurable.display.name");
  }
}
