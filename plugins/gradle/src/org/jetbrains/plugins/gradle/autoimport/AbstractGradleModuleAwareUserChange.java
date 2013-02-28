/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.autoimport;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/19/13 8:48 AM
 */
public abstract class AbstractGradleModuleAwareUserChange<T extends AbstractGradleModuleAwareUserChange<T>>
  extends AbstractGradleUserProjectChange<T>
{

  @Nullable
  private String myModuleName;

  @SuppressWarnings("UnusedDeclaration")
  protected AbstractGradleModuleAwareUserChange() {
    // Required for IJ serialization
  }

  @SuppressWarnings("NullableProblems")
  protected AbstractGradleModuleAwareUserChange(@NotNull String moduleName) {
    myModuleName = moduleName;
  }

  @Nullable
  public String getModuleName() {
    return myModuleName;
  }

  public void setModuleName(@Nullable String moduleName) {
    myModuleName = moduleName;
  }

  @Override
  public int hashCode() {
    return myModuleName != null ? myModuleName.hashCode() : 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractGradleModuleAwareUserChange<?> change = (AbstractGradleModuleAwareUserChange<?>)o;

    if (myModuleName != null ? !myModuleName.equals(change.myModuleName) : change.myModuleName != null) return false;

    return true;
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(@NotNull GradleUserProjectChange<?> o) {
    if (!(o instanceof AbstractGradleModuleAwareUserChange<?>)) {
      return super.compareTo(o);
    }
    AbstractGradleModuleAwareUserChange<T> that = (AbstractGradleModuleAwareUserChange<T>)o;
    if (myModuleName == null) {
      return that.myModuleName == null ? 0 : 1;
    }
    else if (that.myModuleName == null) {
      return -1;
    }
    else {
      return myModuleName.compareTo(that.myModuleName);
    }
  }
}
