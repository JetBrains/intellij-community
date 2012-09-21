/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.*;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 9/17/12
 */
public class ConfigurableWrapper implements SearchableConfigurable, Configurable.Composite {

  private static final ConfigurableWrapper[] EMPTY_ARRAY = new ConfigurableWrapper[0];

  @Nullable
  public static <T extends UnnamedConfigurable> T wrapConfigurable(ConfigurableEP<T> ep) {
    return ep.displayName != null || ep.key != null ? (T)new ConfigurableWrapper(ep) : ep.createConfigurable();
  }

  public static <T extends UnnamedConfigurable> List<T> createConfigurables(ExtensionPointName<? extends ConfigurableEP<T>> name) {
    return ContainerUtil.mapNotNull(Extensions.getExtensions(name), new NullableFunction<ConfigurableEP<T>, T>() {
      @Override
      public T fun(ConfigurableEP<T> ep) {
        return wrapConfigurable(ep);
      }
    });
  }

  public static boolean isNoScroll(Configurable configurable) {
    return configurable instanceof NoScroll ||
           (configurable instanceof ConfigurableWrapper && ((ConfigurableWrapper)configurable).getConfigurable() instanceof NoScroll);
  }

  public static boolean isNonDefaultProject(Configurable configurable) {
    return configurable instanceof NonDefaultProjectConfigurable ||
           (configurable instanceof ConfigurableWrapper && ((ConfigurableWrapper)configurable).myEp.nonDefaultProject);
  }

  private final ConfigurableEP myEp;
  private final ConfigurableWrapper[] myKids;

  public ConfigurableWrapper(ConfigurableEP ep) {
    myEp = ep;
    myKids = ep.children == null ? EMPTY_ARRAY : ContainerUtil.mapNotNull(ep.getChildren(),
                                                                          new NullableFunction<ConfigurableEP, ConfigurableWrapper>() {
                                                                            @Override
                                                                            public ConfigurableWrapper fun(ConfigurableEP ep) {
                                                                              return ep.isAvailable() ? new ConfigurableWrapper(ep) : null;
                                                                            }
                                                                          }, new ConfigurableWrapper[0]);
  }

  private UnnamedConfigurable myConfigurable;

  private UnnamedConfigurable getConfigurable() {
    if (myConfigurable == null) {
      myConfigurable = myEp.createConfigurable();
      if (myConfigurable == null) {
        System.out.println("oops");
      }
    }
    return myConfigurable;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myEp.getDisplayName();
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    UnnamedConfigurable configurable = getConfigurable();
    return configurable instanceof Configurable ? ((Configurable)configurable).getHelpTopic() : null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return getConfigurable().createComponent();
  }

  @Override
  public boolean isModified() {
    return getConfigurable().isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    getConfigurable().apply();
  }

  @Override
  public void reset() {
    getConfigurable().reset();
  }

  @Override
  public void disposeUIResources() {
    getConfigurable().disposeUIResources();
  }

  @Override
  public Configurable[] getConfigurables() {
    return myKids;
  }

  @NotNull
  @Override
  public String getId() {
    return myEp.id == null ? myEp.instanceClass : myEp.id;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    final UnnamedConfigurable configurable = getConfigurable();
    return configurable instanceof SearchableConfigurable ? ((SearchableConfigurable)configurable).enableSearch(option) : null;
  }
}
