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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.*;
import com.intellij.util.ArrayUtil;
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
public class ConfigurableWrapper implements SearchableConfigurable {

  private static final ConfigurableWrapper[] EMPTY_ARRAY = new ConfigurableWrapper[0];
  private static final NullableFunction<ConfigurableEP<Configurable>,Configurable> CONFIGURABLE_FUNCTION = new NullableFunction<ConfigurableEP<Configurable>, Configurable>() {
    @Override
    public Configurable fun(ConfigurableEP<Configurable> ep) {
      return wrapConfigurable(ep);
    }
  };
  private static final Logger LOG = Logger.getInstance(ConfigurableWrapper.class);

  @Nullable
  public static <T extends UnnamedConfigurable> T wrapConfigurable(ConfigurableEP<T> ep) {
    if (!ep.canCreateConfigurable()) {
      return null;
    }
    if (ep.displayName != null || ep.key != null || ep.groupId != null) {
      T configurable = null;
      if (ep.providerClass != null) {
        configurable = ep.createConfigurable();
        if (configurable == null) {
          return null; // it is allowed to return null from provider
        }
      }
      return !ep.dynamic && ep.children == null && ep.childrenEPName == null
             ? (T)new ConfigurableWrapper(ep, configurable)
             : (T)new CompositeWrapper(ep, configurable);
    }
    else {
      return ep.createConfigurable();
    }
  }

  public static <T extends UnnamedConfigurable> List<T> createConfigurables(ExtensionPointName<? extends ConfigurableEP<T>> name) {
    return ContainerUtil.mapNotNull(name.getExtensions(), new NullableFunction<ConfigurableEP<T>, T>() {
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

  private ConfigurableWrapper(@NotNull ConfigurableEP ep, @Nullable UnnamedConfigurable configurable) {
    myEp = ep;
    myConfigurable = configurable;
  }

  private UnnamedConfigurable myConfigurable;

  public UnnamedConfigurable getConfigurable() {
    if (myConfigurable == null) {
      myConfigurable = myEp.createConfigurable();
      if (myConfigurable == null) {
        LOG.error("Can't instantiate configurable for " + myEp);
      }
    }
    return myConfigurable;
  }

  @Nls
  @Override
  public String getDisplayName() {
    if (myEp.displayName == null && myEp.key == null) {
      UnnamedConfigurable configurable = getConfigurable();
      if (configurable instanceof Configurable) {
        return ((Configurable)configurable).getDisplayName();
      }
    }
    return myEp.getDisplayName();
  }

  public String getInstanceClass() {
    return myEp.instanceClass;
  }
  public String getProviderClass() {
    return myEp.providerClass;
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

  @NotNull
  @Override
  public String getId() {
    if (myEp.id != null) {
      return myEp.id;
    }
    UnnamedConfigurable configurable = getConfigurable();
    if (configurable instanceof SearchableConfigurable) {
      return ((SearchableConfigurable)configurable).getId();
    }
    return myEp.instanceClass != null
           ? myEp.instanceClass
           : myEp.providerClass;
  }

  @NotNull
  public ConfigurableEP getExtensionPoint() {
    return myEp;
  }

  public String getParentId() {
    return myEp.parentId;
  }

  public ConfigurableWrapper addChild(Configurable configurable) {
    return new CompositeWrapper(myEp, null, configurable);
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    final UnnamedConfigurable configurable = getConfigurable();
    return configurable instanceof SearchableConfigurable ? ((SearchableConfigurable)configurable).enableSearch(option) : null;
  }

  private static class CompositeWrapper extends ConfigurableWrapper implements Configurable.Composite {

    private Configurable[] myKids;

    private CompositeWrapper(@NotNull ConfigurableEP ep, @Nullable UnnamedConfigurable configurable, Configurable... kids) {
      super(ep, configurable);
      if (ep.dynamic) {
        kids = ((Composite)getConfigurable()).getConfigurables();
      }
      else if (ep.children != null) {
        kids = ContainerUtil.mapNotNull(ep.getChildren(),
                                        new NullableFunction<ConfigurableEP, ConfigurableWrapper>() {
                                          @Override
                                          public ConfigurableWrapper fun(ConfigurableEP ep) {
                                            return ep.isAvailable() ? (ConfigurableWrapper)wrapConfigurable(ep) : null;
                                          }
                                        }, EMPTY_ARRAY);
      }
      if (ep.childrenEPName != null) {
        ExtensionPoint<Object> childrenEP = Extensions.getArea(ep.getProject()).getExtensionPoint(ep.childrenEPName);
        Object[] extensions = childrenEP.getExtensions();
        if (extensions.length > 0) {
          if (extensions[0] instanceof ConfigurableEP) {
            Configurable[] children = ContainerUtil.mapNotNull(((ConfigurableEP<Configurable>[])extensions),
                                                         CONFIGURABLE_FUNCTION, new Configurable[0]);
            kids = ArrayUtil.mergeArrays(kids, children);
          }
          else {
            kids = ArrayUtil.mergeArrays(kids, ((Composite)getConfigurable()).getConfigurables());
          }
        }
      }
      myKids = kids;
    }

    @Override
    public Configurable[] getConfigurables() {
      return myKids;
    }

    @Override
    public ConfigurableWrapper addChild(Configurable configurable) {
      myKids = ArrayUtil.append(myKids, configurable);
      return this;
    }
  }
}
