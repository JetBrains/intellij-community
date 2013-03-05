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

/**
 * @author Denis Zhdanov
 * @since 3/4/13 12:17 PM
 */
public abstract class AbstractGradleDependencyExportedUserChange<T extends AbstractGradleDependencyExportedUserChange<T>> 
  extends AbstractGradleDependencyUserChange<T>
{
  private boolean myExported;

  protected AbstractGradleDependencyExportedUserChange() {
    // Required for IJ serialization
  }

  protected AbstractGradleDependencyExportedUserChange(@NotNull String moduleName, @NotNull String dependencyName, boolean exported) {
    super(moduleName, dependencyName);
    myExported = exported;
  }

  public boolean isExported() {
    return myExported;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setExported(boolean exported) {
    myExported = exported;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    return 31 * result + (myExported ? 1 : 0);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    AbstractGradleDependencyExportedUserChange<?> change = (AbstractGradleDependencyExportedUserChange<?>)o;

    return myExported == change.myExported;
  }

  @SuppressWarnings({"unchecked"})
  @Override
  public int compareTo(@NotNull GradleUserProjectChange<?> o) {
    int cmp = super.compareTo(o);
    if (cmp != 0 || (!(o instanceof AbstractGradleDependencyExportedUserChange<?>))) {
      return cmp;
    }

    AbstractGradleDependencyExportedUserChange<T> that = (AbstractGradleDependencyExportedUserChange<T>)o;
    if (myExported) {
      return that.myExported ? 0 : 1;
    }
    else {
      return that.myExported ? -1 : 0;
    }
  }
}
