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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/16/2016
 */
public class DefaultGradleExtensions extends ArrayList<GradleExtension> implements GradleExtensions {
  private static final long serialVersionUID = 1L;

  public DefaultGradleExtensions() {
  }

  public DefaultGradleExtensions(GradleExtensions extensions) {
    for (GradleExtension extension : extensions.list()) {
      add(new DefaultGradleExtension(extension));
    }
  }

  @Override
  public List<GradleExtension> list() {
    return this;
  }
}
