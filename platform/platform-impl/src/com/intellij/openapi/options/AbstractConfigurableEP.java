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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AbstractConfigurableEP<T extends UnnamedConfigurable> extends AbstractExtensionPointBean {
  @Attribute("instance")
  public String instanceClass;

  /**
   * @deprecated
   */
  @Attribute("implementation")
  public String implementationClass;

  private final PicoContainer myPicoContainer;
  private final AtomicNotNullLazyValue<T> myImplementation = new AtomicNotNullLazyValue<T>() {
    @NotNull
    @Override
    protected T compute() {
      if (implementationClass == null) {
        throw new IllegalArgumentException("Neither 'instance' nor 'implementation' attribute is not specified for " + AbstractConfigurableEP.this.getClass() + " extension");
      }
      try {
        final Class<T> aClass = findClass(implementationClass);
        return instantiate(aClass, myPicoContainer, true);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  };

  protected AbstractConfigurableEP(PicoContainer picoContainer) {
    myPicoContainer = picoContainer;
  }

  public AbstractConfigurableEP() {
    myPicoContainer = ApplicationManager.getApplication().getPicoContainer();
  }

  @NotNull
  public T createConfigurable() {
    if (instanceClass == null) {
      return myImplementation.getValue();
    }
    try {
      final Class<T> aClass = findClass(instanceClass);
      return instantiate(aClass, myPicoContainer);
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T extends UnnamedConfigurable> List<T> createConfigurables(ExtensionPointName<? extends AbstractConfigurableEP<T>> pointName) {
    List<T> configurables = new ArrayList<T>();
    for (AbstractConfigurableEP<T> ep : pointName.getExtensions()) {
      configurables.add(ep.createConfigurable());
    }
    return configurables;
  }
}
