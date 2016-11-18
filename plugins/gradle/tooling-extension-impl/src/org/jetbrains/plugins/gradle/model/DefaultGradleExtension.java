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
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 11/16/2016
 */
public class DefaultGradleExtension implements GradleExtension {
  private static final long serialVersionUID = 1L;
  @NotNull
  private final String myName;
  @NotNull
  private final String myRootTypeFqn;
  @Nullable
  private final String myNamedObjectTypeFqn;

  public DefaultGradleExtension(@NotNull String name, @NotNull String rootTypeFqn, @Nullable String namedObjectTypeFqn) {
    myName = name;
    myRootTypeFqn = rootTypeFqn;
    myNamedObjectTypeFqn = namedObjectTypeFqn;
  }

  public DefaultGradleExtension(GradleExtension extension) {
    this(extension.getName(), extension.getRootTypeFqn(), extension.getNamedObjectTypeFqn());
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getRootTypeFqn() {
    return myRootTypeFqn;
  }

  @Nullable
  @Override
  public String getNamedObjectTypeFqn() {
    return myNamedObjectTypeFqn;
  }
}
