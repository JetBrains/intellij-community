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
package com.intellij.internal.statistic.ideSettings;

import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public class IdeSettingsStatisticsUtils {
  public static final GroupDescriptor GROUP = GroupDescriptor.create("IDE Settings", GroupDescriptor.HIGHER_PRIORITY);

  @Nullable
  public static Object getApplicationProvider(@NotNull String providerName) {
    return getProviderInstance(getApplicationComponentByName(providerName));
  }

  public static Set<UsageDescriptor> getUsages(@NotNull IdeSettingsDescriptor descriptor, @NotNull Object componentInstance) {
    Set<UsageDescriptor> descriptors = new HashSet<>();

    String providerName = descriptor.myProviderName;

    List<String> propertyNames = descriptor.getPropertyNames();
    if (providerName != null && propertyNames.size() > 0) {
        for (String propertyName : propertyNames) {
          Object propertyValue = getPropertyValue(componentInstance, propertyName);

          if (propertyValue != null) {
            descriptors.add(new UsageDescriptor(getUsageDescriptorKey(providerName, propertyName, propertyValue.toString()), 1));
          }
      }
    }
    return descriptors;
  }

  @Nullable
  private static Object getPropertyValue(Object componentInstance, String propertyName) {
    final Class<? extends Object> componentInstanceClass = componentInstance.getClass();
    Object propertyValue = ReflectionUtil.getField(componentInstanceClass, componentInstance, null, propertyName);
    if (propertyValue == null) {
      Method method = ReflectionUtil.getMethod(componentInstanceClass, "get" + StringUtil.capitalize(propertyName));
      if (method == null) {
        method = ReflectionUtil.getMethod(componentInstanceClass, "is" + StringUtil.capitalize(propertyName));
      }
      if (method != null) {
        try {
          propertyValue = method.invoke(componentInstance);
        }
        catch (Exception ignored) {
        }
      }
    }
    return propertyValue;
  }

  private static String getUsageDescriptorKey(@NotNull String providerName, @NotNull String name, @NotNull String value) {
    final String shortName = StringUtil.getShortName(providerName);
    return shortName + "#" + name + "(" + value + ")";
  }

  @Nullable
  public static Object getProjectProvider(@Nullable Project project,
                                          @NotNull String providerName) {
    return getProviderInstance(getProjectComponentByName(project, providerName));
  }

  @Nullable
  private static Object getProviderInstance(Object componentInstance) {
    if (componentInstance instanceof PersistentStateComponent) {
      return ((PersistentStateComponent)componentInstance).getState();
    }

    return componentInstance;
  }

  @Nullable
  private static Object getApplicationComponentByName(@NotNull String providerName) {
    return ApplicationManager.getApplication().getPicoContainer().getComponentInstance(providerName);
  }

  @Nullable
  private static Object getProjectComponentByName(@Nullable Project project, String providerName) {
    if (project != null) {
      return project.getPicoContainer().getComponentInstance(providerName);
    }
    return null;
  }
}
