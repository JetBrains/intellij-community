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
package org.jetbrains.plugins.groovy.extensions.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;

public class NamedArgumentDescriptorBase implements NamedArgumentDescriptor {

  private final @NotNull Priority myPriority;

  public NamedArgumentDescriptorBase() {
    myPriority = Priority.ALWAYS_ON_TOP;
  }

  public NamedArgumentDescriptorBase(@NotNull Priority priority) {
    myPriority = priority;
  }

  @NotNull
  @Override
  public Priority getPriority() {
    return myPriority;
  }
}
