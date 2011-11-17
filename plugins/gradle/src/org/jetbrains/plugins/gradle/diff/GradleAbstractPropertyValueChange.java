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
 * Defines contract for change that points to particular property value change.
 * 
 * @author Denis Zhdanov
 * @since 11/15/11 7:57 PM
 */
public abstract class GradleAbstractPropertyValueChange<T> extends GradleAbstractProjectStructureChange {

  private final String myProperyName;
  private final T      myGradleValue;
  private final T      myIntellijValue;

  public GradleAbstractPropertyValueChange(@NotNull String propertyName, @Nullable T gradleValue, @Nullable T intellijValue) {
    myProperyName = propertyName;
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
    return 31 * result + (myIntellijValue != null ? myIntellijValue.hashCode() : 0);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    GradleAbstractPropertyValueChange that = (GradleAbstractPropertyValueChange)o;

    if (myGradleValue != null ? !myGradleValue.equals(that.myGradleValue) : that.myGradleValue != null) return false;
    if (myIntellijValue != null ? !myIntellijValue.equals(that.myIntellijValue) : that.myIntellijValue != null) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s change: gradle='%s', intellij='%s'", myProperyName, myGradleValue, myIntellijValue);
  }
}
