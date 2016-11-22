/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/16/2016
 */
public class DefaultGradleExtensions implements GradleExtensions {
  private static final long serialVersionUID = 1L;
  @NotNull
  private final List<GradleExtension> myExtensions = new ArrayList<GradleExtension>();
  @NotNull
  private final List<GradleProperty> myGradleProperties = new ArrayList<GradleProperty>();

  public DefaultGradleExtensions() {
  }

  public DefaultGradleExtensions(GradleExtensions extensions) {
    for (GradleExtension extension : extensions.getExtensions()) {
      myExtensions.add(new DefaultGradleExtension(extension));
    }
    for (GradleProperty property : extensions.getGradleProperties()) {
      myGradleProperties.add(new DefaultGradleProperty(property));
   }
  }

  @NotNull
  @Override
  public List<GradleExtension> getExtensions() {
    return myExtensions;
  }

  @NotNull
  @Override
  public List<GradleProperty> getGradleProperties() {
    return myGradleProperties;
  }
}
