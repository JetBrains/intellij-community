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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines general contract for a change that encapsulates information about conflicting property value of the matched gradle
 * and intellij entities.
 * <p/>
 * For example we may match particular gradle library to an intellij library but they may have different set of attached binaries.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/15/11 7:57 PM
 * @param <T>   target property value type
 */
public abstract class GradleAbstractConflictingPropertyChange<T> extends GradleAbstractProjectStructureChange {
  
  private final String myPropertyDescription;
  private final T myGradleValue;
  private final T myIntellijValue;

  public GradleAbstractConflictingPropertyChange(@NotNull String propertyDescription, @Nullable T gradleValue, @Nullable T intellijValue) {
    myPropertyDescription = propertyDescription;
    myGradleValue = gradleValue;
    myIntellijValue = intellijValue;
  }

  /**
   * @return    target property's value at Gradle side
   */
  @Nullable
  public T getGradleValue() {
    return myGradleValue;
  }

  /**
   * @return    target property's value at IntelliJ IDEA side
   */
  @Nullable
  public T getIntellijValue() {
    return myIntellijValue;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myGradleValue != null ? myGradleValue.hashCode() : 0);
    return  31 * result + (myIntellijValue != null ? myIntellijValue.hashCode() : 0);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    GradleAbstractConflictingPropertyChange that = (GradleAbstractConflictingPropertyChange)o;

    if (myGradleValue != null ? !myGradleValue.equals(that.myGradleValue) : that.myGradleValue != null) return false;
    if (myIntellijValue != null ? !myIntellijValue.equals(that.myIntellijValue) : that.myIntellijValue != null) return false;
    return true;
  }

  @Override
  public String toString() {
    return String.format("%s change: gradle='%s', intellij='%s'", myPropertyDescription, myGradleValue, myIntellijValue);
  }
}
