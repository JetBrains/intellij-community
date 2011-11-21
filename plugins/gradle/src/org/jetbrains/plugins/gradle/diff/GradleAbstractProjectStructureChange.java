/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.diff;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Denis Zhdanov
 * @since 11/15/11 1:33 PM
 */
public abstract class GradleAbstractProjectStructureChange implements GradleProjectStructureChange {

  private final AtomicBoolean myConfirmed = new AtomicBoolean();

  @Override
  public boolean isConfirmed() {
    return myConfirmed.get();
  }

  @Override
  public void confirm() {
    myConfirmed.set(true);
  }

  @Override
  public int hashCode() {
    return 31;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o != null && getClass() == o.getClass();
  }
}
