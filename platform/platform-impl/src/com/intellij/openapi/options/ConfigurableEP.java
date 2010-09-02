/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * @author nik
 */
public class ConfigurableEP extends AbstractExtensionPointBean {
  @Attribute("implementation")
  public String implementationClass;
  @Attribute("instance")
  public String instanceClass;
  @Attribute("provider")
  public String providerClass;

  private final AtomicNotNullLazyValue<NullableFactory<Configurable>> myFactory;
  private final PicoContainer myPicoContainer;

  public ConfigurableEP() {
    this(ApplicationManager.getApplication().getPicoContainer());
  }

  public ConfigurableEP(Project project) {
    this(project.getPicoContainer());
  }

  private ConfigurableEP(PicoContainer picoContainer) {
    myPicoContainer = picoContainer;
    myFactory = new AtomicNotNullLazyValue<NullableFactory<Configurable>>() {
      @NotNull
      @Override
      protected NullableFactory<Configurable> compute() {
        if (providerClass != null) {
          return new InstanceFromProviderFactory();
        }
        else if (instanceClass != null) {
          return new NewInstanceFactory();
        }
        else if (implementationClass != null) {
          return new ImplementationFactory();
        }
        throw new RuntimeException();
      }
    };
  }

  @Nullable
  public Configurable createConfigurable() {
    return myFactory.getValue().create();
  }

  private class InstanceFromProviderFactory extends AtomicNotNullLazyValue<ConfigurableProvider> implements NullableFactory<Configurable> {
    public Configurable create() {
      return getValue().createConfigurable();
    }

    @NotNull
    @Override
    protected ConfigurableProvider compute() {
      try {
        return instantiate(providerClass, myPicoContainer);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private class NewInstanceFactory extends NotNullLazyValue<Class<? extends Configurable>> implements NullableFactory<Configurable> {
    public Configurable create() {
      return instantiate(getValue(), myPicoContainer, true);
    }

    @NotNull
    @Override
    protected Class<? extends Configurable> compute() {
      try {
        return findClass(instanceClass);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private class ImplementationFactory extends AtomicNotNullLazyValue<Configurable> implements NullableFactory<Configurable> {
    @Override
    public Configurable create() {
      return compute();
    }

    @NotNull
    @Override
    protected Configurable compute() {
      try {
        final Class<Configurable> aClass = findClass(implementationClass);
        return instantiate(aClass, myPicoContainer, true);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
