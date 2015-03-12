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
package com.intellij.util.pico;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.*;
import org.picocontainer.defaults.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * A drop-in replacement of {@link org.picocontainer.defaults.ConstructorInjectionComponentAdapter}
 * The same code (generified and cleaned up) but without constructor caching (hence taking up less memory).
 * This class also inlines instance caching (e.g. it doesn't need to be wrapped in a CachingComponentAdapter).
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public class ConstructorInjectionComponentAdapter extends org.picocontainer.defaults.ConstructorInjectionComponentAdapter {
  private Object myInstance;

  public ConstructorInjectionComponentAdapter(@NotNull Object componentKey, @NotNull Class componentImplementation, Parameter[] parameters, boolean allowNonPublicClasses, ComponentMonitor monitor, LifecycleStrategy lifecycleStrategy) throws AssignabilityRegistrationException, NotConcreteRegistrationException {
    super(componentKey, componentImplementation, parameters, allowNonPublicClasses, monitor, lifecycleStrategy);
  }

  public ConstructorInjectionComponentAdapter(@NotNull Object componentKey, @NotNull Class componentImplementation, Parameter[] parameters, boolean allowNonPublicClasses) throws AssignabilityRegistrationException, NotConcreteRegistrationException {
    super(componentKey, componentImplementation, parameters, allowNonPublicClasses);
  }

  public ConstructorInjectionComponentAdapter(@NotNull Object componentKey, @NotNull Class componentImplementation, Parameter[] parameters) {
    this(componentKey, componentImplementation, parameters, false);
  }

  public ConstructorInjectionComponentAdapter(@NotNull Object componentKey, @NotNull Class componentImplementation) throws AssignabilityRegistrationException, NotConcreteRegistrationException {
    this(componentKey, componentImplementation, null);
  }

  @Override
  public Object getComponentInstance(PicoContainer container) throws PicoInitializationException, PicoIntrospectionException,
                                                                     AssignabilityRegistrationException, NotConcreteRegistrationException {
    Object instance = myInstance;
    if (instance == null) {
      instance = super.getComponentInstance(container);
      myInstance = instance;
    }
    return instance;
  }

  protected Constructor getGreediestSatisfiableConstructor(PicoContainer container) throws
                                                                                    PicoIntrospectionException,
                                                                                    AssignabilityRegistrationException, NotConcreteRegistrationException {
    final Set<Constructor> conflicts = new HashSet<Constructor>();
    final Set<List<Class>> unsatisfiableDependencyTypes = new HashSet<List<Class>>();
    List<Constructor> sortedMatchingConstructors = getSortedMatchingConstructors();
    Constructor greediestConstructor = null;
    int lastSatisfiableConstructorSize = -1;
    Class unsatisfiedDependencyType = null;
    for (Constructor constructor : sortedMatchingConstructors) {
      boolean failedDependency = false;
      Class[] parameterTypes = constructor.getParameterTypes();
      Parameter[] currentParameters = parameters != null ? parameters : createDefaultParameters(parameterTypes);

      // remember: all constructors with less arguments than the given parameters are filtered out already
      for (int j = 0; j < currentParameters.length; j++) {
        // check whether this constructor is satisfiable
        if (currentParameters[j].isResolvable(container, this, parameterTypes[j])) {
          continue;
        }
        unsatisfiableDependencyTypes.add(Arrays.asList(parameterTypes));
        unsatisfiedDependencyType = parameterTypes[j];
        failedDependency = true;
        break;
      }

      if (greediestConstructor != null && parameterTypes.length != lastSatisfiableConstructorSize) {
        if (conflicts.isEmpty()) {
          // we found our match [aka. greedy and satisfied]
          return greediestConstructor;
        }
        else {
          // fits although not greedy
          conflicts.add(constructor);
        }
      }
      else if (!failedDependency && lastSatisfiableConstructorSize == parameterTypes.length) {
        // satisfied and same size as previous one?
        conflicts.add(constructor);
        conflicts.add(greediestConstructor);
      }
      else if (!failedDependency) {
        greediestConstructor = constructor;
        lastSatisfiableConstructorSize = parameterTypes.length;
      }
    }
    if (!conflicts.isEmpty()) {
      throw new TooManySatisfiableConstructorsException(getComponentImplementation(), conflicts);
    } else if (greediestConstructor == null && !unsatisfiableDependencyTypes.isEmpty()) {
      throw new UnsatisfiableDependenciesException(this, unsatisfiedDependencyType, unsatisfiableDependencyTypes, container);
    } else if (greediestConstructor == null) {
      // be nice to the user, show all constructors that were filtered out
      final Set<Constructor> nonMatching = ContainerUtil.newHashSet(getConstructors());
      throw new PicoInitializationException("Either do the specified parameters not match any of the following constructors: " + nonMatching.toString() + " or the constructors were not accessible for '" + getComponentImplementation() + "'");
    }
    return greediestConstructor;
  }

  private List<Constructor> getSortedMatchingConstructors() {
    List<Constructor> matchingConstructors = new ArrayList<Constructor>();
    // filter out all constructors that will definitely not match
    for (Constructor constructor : getConstructors()) {
      if ((parameters == null || constructor.getParameterTypes().length == parameters.length) &&
          (allowNonPublicClasses || (constructor.getModifiers() & Modifier.PUBLIC) != 0)) {
        matchingConstructors.add(constructor);
      }
    }
    // optimize list of constructors moving the longest at the beginning
    if (parameters == null) {
      Collections.sort(matchingConstructors, new Comparator<Constructor>() {
        public int compare(Constructor arg0, Constructor arg1) {
          return arg1.getParameterTypes().length - arg0.getParameterTypes().length;
        }
      });
    }
    return matchingConstructors;
  }

  private Constructor[] getConstructors() {
    return (Constructor[]) AccessController.doPrivileged(new PrivilegedAction() {
      public Object run() {
        return getComponentImplementation().getDeclaredConstructors();
      }
    });
  }
}