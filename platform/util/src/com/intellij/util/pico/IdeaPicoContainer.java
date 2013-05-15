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

package com.intellij.util.pico;

import org.jetbrains.annotations.NotNull;
import org.picocontainer.*;
import org.picocontainer.defaults.*;
import org.picocontainer.monitors.DefaultComponentMonitor;

import java.util.*;

public class IdeaPicoContainer extends DefaultPicoContainer {

  public IdeaPicoContainer() {
    this(null);
  }

  public IdeaPicoContainer(final PicoContainer parent) {
    super(new MyComponentAdapterFactory(), parent);
  }

  private static class MyComponentAdapterFactory extends MonitoringComponentAdapterFactory {
    private final LifecycleStrategy myLifecycleStrategy;

    private MyComponentAdapterFactory() {
      myLifecycleStrategy = new DefaultLifecycleStrategy(new DefaultComponentMonitor());
    }

    @Override
    public ComponentAdapter createComponentAdapter(@NotNull Object componentKey, @NotNull Class componentImplementation, Parameter[] parameters)
      throws PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
      return new CachingComponentAdapter(
        new ConstructorInjectionComponentAdapter(componentKey, componentImplementation, parameters, true, currentMonitor(), myLifecycleStrategy));
    }

    @Override
    public void changeMonitor(ComponentMonitor monitor) {
      super.changeMonitor(monitor);
      if (myLifecycleStrategy instanceof ComponentMonitorStrategy) {
        ((ComponentMonitorStrategy)myLifecycleStrategy).changeMonitor(monitor);
      }
    }
  }



  @Override
  public ComponentAdapter getComponentAdapterOfType(final Class componentType) {
    return super.getComponentAdapterOfType(componentType);
  }

  @Override
  public List getComponentAdaptersOfType(final Class componentType) {
    if (componentType == null) return Collections.emptyList();
    if (componentType == String.class) return Collections.emptyList();

    List<ComponentAdapter> result = new ArrayList<ComponentAdapter>();

    final Map<String,ComponentAdapter> cache = getAssignablesCache();
    final ComponentAdapter cacheHit = cache.get(componentType.getName());
    if (cacheHit != null) {
      result.add(cacheHit);
    }

    result.addAll(getNonAssignableAdaptersOfType(componentType));
    return result;
  }
}
