// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class ConfigurableWrapper implements SearchableConfigurable, Weighted, HierarchicalConfigurable {
  static final Logger LOG = Logger.getInstance(ConfigurableWrapper.class);

  @Nullable
  public static <T extends UnnamedConfigurable> T wrapConfigurable(@NotNull ConfigurableEP<T> ep) {
    return wrapConfigurable(ep, false);
  }

  @Nullable
  public static <T extends UnnamedConfigurable> T wrapConfigurable(@NotNull ConfigurableEP<T> ep, boolean settings) {
    if (!ep.canCreateConfigurable()) {
      return null;
    }
    if (settings || ep.displayName != null || ep.key != null || ep.parentId != null || ep.groupId != null) {
      //noinspection unchecked
      return (T)(!ep.dynamic && ep.children == null && ep.childrenEPName == null ? new ConfigurableWrapper(ep) : new CompositeWrapper(ep));
    }
    return createConfigurable(ep, LOG.isDebugEnabled());
  }

  @Nullable
  private static <T extends UnnamedConfigurable> T createConfigurable(@NotNull ConfigurableEP<T> ep, boolean log) {
    long time = System.currentTimeMillis();
    T configurable = ep.createConfigurable();
    if (configurable instanceof Configurable) {
      ConfigurableCardPanel.warn((Configurable)configurable, "init", time);
      if (log) {
        LOG.debug("cannot create configurable wrapper for " + configurable.getClass());
      }
    }
    return configurable;
  }

  public static <T extends UnnamedConfigurable> List<T> createConfigurables(@NotNull ExtensionPointName<? extends ConfigurableEP<T>> name) {
    Collection<? extends ConfigurableEP<T>> collection = name.getExtensionList();
    if (collection.isEmpty()) {
      return Collections.emptyList();
    }

    List<T> result = new ArrayList<>(collection.size());
    for (ConfigurableEP<T> item : collection) {
      T o = wrapConfigurable(item, false);
      if (o != null) {
        result.add(o);
      }
    }
    return result.isEmpty() ? Collections.emptyList() : result;
  }

  public static boolean hasOwnContent(UnnamedConfigurable configurable) {
    SearchableConfigurable.Parent parent = cast(SearchableConfigurable.Parent.class, configurable);
    return parent != null && parent.hasOwnContent();
  }

  public static boolean isNonDefaultProject(Configurable configurable) {
    //noinspection deprecation
    return configurable instanceof NonDefaultProjectConfigurable ||
           (configurable instanceof ConfigurableWrapper && ((ConfigurableWrapper)configurable).myEp.nonDefaultProject);
  }

  @Nullable
  public static <T> T cast(@NotNull Class<T> type, @Nullable UnnamedConfigurable configurable) {
    if (configurable instanceof ConfigurableWrapper) {
      ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
      if (wrapper.myConfigurable == null) {
        Class<?> configurableType = wrapper.getExtensionPoint().getConfigurableType();
        if (configurableType != null) {
          if (!type.isAssignableFrom(configurableType)) {
            return null; // do not create configurable that cannot be cast to the specified type
          }
        }
      }
      configurable = wrapper.getConfigurable();
    }
    return type.isInstance(configurable)
           ? type.cast(configurable)
           : null;
  }

  private final ConfigurableEP<?> myEp;
  int myWeight; // see ConfigurableExtensionPointUtil.getConfigurableToReplace

  private ConfigurableWrapper(@NotNull ConfigurableEP<?> ep) {
    myEp = ep;
    myWeight = ep.groupWeight;
  }

  @Nullable
  private UnnamedConfigurable myConfigurable;

  @Nullable
  public UnnamedConfigurable getRawConfigurable() {
    return myConfigurable;
  }

  public UnnamedConfigurable getConfigurable() {
    if (myConfigurable == null) {
      myConfigurable = createConfigurable(myEp, false);
      if (myConfigurable == null) {
        LOG.error("Can't instantiate configurable for " + myEp);
      }
      else if (LOG.isDebugEnabled()) {
        LOG.debug("created configurable for " + myConfigurable.getClass());
      }
    }
    return myConfigurable;
  }

  @Override
  public int getWeight() {
    return myWeight;
  }

  @Nls
  @Override
  public String getDisplayName() {
    if (myEp.displayName == null && myEp.key == null) {
      boolean loaded = myConfigurable != null;
      Configurable configurable = cast(Configurable.class, this);
      if (configurable != null) {
        String name = configurable.getDisplayName();
        if (!loaded && LOG.isDebugEnabled()) {
          LOG.debug("XML does not provide displayName for " + configurable.getClass());
        }
        return name;
      }
    }
    return myEp.getDisplayName();
  }

  public String getProviderClass() {
    return myEp.providerClass;
  }

  @Nullable
  public Project getProject() {
    return myEp.getProject();
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
    UnnamedConfigurable configurable = getConfigurable();
    return configurable == null ? null : configurable.createComponent();
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
    UnnamedConfigurable configurable = myConfigurable;
    if (configurable != null) {
      configurable.disposeUIResources();
      myConfigurable = null;
    }
  }

  @Override
  public void cancel() {
    UnnamedConfigurable configurable = myConfigurable;
    if (configurable != null) {
      configurable.cancel();
    }
  }

  @NotNull
  @Override
  public String getId() {
    if (myEp.id != null) {
      return myEp.id;
    }
    boolean loaded = myConfigurable != null;
    SearchableConfigurable configurable = cast(SearchableConfigurable.class, this);
    if (configurable != null) {
      String id = configurable.getId();
      if (!loaded) {
        LOG.debug("XML does not provide id for " + configurable.getClass());
      }
      return id;
    }
    // order from #ConfigurableEP(PicoContainer, Project)
    return myEp.providerClass != null
           ? myEp.providerClass
           : myEp.instanceClass != null
             ? myEp.instanceClass
             : myEp.implementationClass;
  }

  @NotNull
  public ConfigurableEP<?> getExtensionPoint() {
    return myEp;
  }

  @Override
  public String getParentId() {
    return myEp.parentId;
  }

  @Override
  public ConfigurableWrapper addChild(Configurable configurable) {
    return new CompositeWrapper(myEp, configurable);
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

  @NotNull
  @Override
  public Class<?> getOriginalClass() {
    final UnnamedConfigurable configurable = getConfigurable();
    return configurable instanceof SearchableConfigurable
           ? ((SearchableConfigurable)configurable).getOriginalClass()
           : configurable != null
             ? configurable.getClass()
             : getClass();
  }

  private static final class CompositeWrapper extends ConfigurableWrapper implements Configurable.Composite {

    private Configurable[] myKids;
    private Comparator<Configurable> myComparator;
    private boolean isInitialized;

    private CompositeWrapper(@NotNull ConfigurableEP ep, Configurable... kids) {
      super(ep);
      myKids = kids;
    }

    @Override
    public Configurable @NotNull [] getConfigurables() {
      if (isInitialized) {
        return myKids;
      }

      long time = System.currentTimeMillis();
      ArrayList<Configurable> list = new ArrayList<>();
      if (super.myEp.dynamic) {
        Composite composite = cast(Composite.class, this);
        if (composite != null) {
          Collections.addAll(list, composite.getConfigurables());
        }
      }
      if (super.myEp.children != null) {
        for (ConfigurableEP<?> ep : super.myEp.getChildren()) {
          if (ep.isAvailable()) {
            list.add((Configurable)wrapConfigurable(ep));
          }
        }
      }
      if (super.myEp.childrenEPName != null) {
        Project project = super.myEp.getProject();
        ExtensionsArea area = project == null ? ApplicationManager.getApplication().getExtensionArea() : project.getExtensionArea();
        ExtensionPoint<Object> point = area.getExtensionPointIfRegistered(super.myEp.childrenEPName);
        List<Object> extensions;
        if (point == null) {
          LOG.warn("Cannot find extension point " + super.myEp.childrenEPName + " in " + area);
          extensions = Collections.emptyList();
        }
        else {
          extensions = point.getExtensionList();
        }
        if (!extensions.isEmpty()) {
          if (extensions.get(0) instanceof ConfigurableEP) {
            for (Object object : extensions) {
              list.add((Configurable)wrapConfigurable((ConfigurableEP<?>)object));
            }
          }
          else if (!super.myEp.dynamic) {
            Composite composite = cast(Composite.class, this);
            if (composite != null) {
              Collections.addAll(list, composite.getConfigurables());
            }
          }
        }
      }
      Collections.addAll(list, myKids);
      // sort configurables is needed
      for (Configurable configurable : list) {
        if (configurable instanceof Weighted) {
          if (((Weighted)configurable).getWeight() != 0) {
            myComparator = COMPARATOR;
            list.sort(myComparator);
            break;
          }
        }
      }
      myKids = list.toArray(new Configurable[0]);
      isInitialized = true;
      ConfigurableCardPanel.warn(this, "children", time);
      return myKids;
    }

    @Override
    public ConfigurableWrapper addChild(Configurable configurable) {
      if (myComparator != null) {
        int index = Arrays.binarySearch(myKids, configurable, myComparator);
        LOG.assertTrue(index < 0, "similar configurable is already exist");
        myKids = ArrayUtil.insert(myKids, -1 - index, configurable);
      }
      else {
        myKids = ArrayUtil.append(myKids, configurable);
      }
      return this;
    }
  }
}
