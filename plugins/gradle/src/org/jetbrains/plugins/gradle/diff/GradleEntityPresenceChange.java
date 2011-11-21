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

import org.jetbrains.annotations.Nullable;

/**
 * Defines common interface for a change that indicates that particular entity has been added/removed at Gradle or IntelliJ IDEA.
 * 
 * @author Denis Zhdanov
 * @since 11/17/11 12:43 PM
 * @param <G>   entity type at Gradle side
 * @param <I>   entity type at IntelliJ IDEA side
 */
public abstract class GradleEntityPresenceChange<G, I> extends GradleAbstractProjectStructureChange {

  private final G myGradleEntity;
  private final I myIntellijEntity;

  /**
   * Creates new <code>GradleEntityPresenceChange</code> project.
   *
   * @param gradleEntity    target entity at Gradle side. <code>null</code> as indication that the entity was removed at Gradle side
   *                        or added at IntelliJ side
   * @param intellijEntity  target entity at IntelliJ IDEA side. <code>null</code> as indication that the entity was removed
   *                        at IntelliJ IDEA side or added at IntelliJ side
   * @throws IllegalArgumentException    if both of the given entities are defined or undefined. Expecting this constructor to be
   *                                     called with one <code>null</code> argument and one non-<code>null</code> argument
   */
  public GradleEntityPresenceChange(@Nullable G gradleEntity, @Nullable I intellijEntity) throws IllegalArgumentException {
    if (gradleEntity  == null ^ intellijEntity  == null) {
      throw new IllegalArgumentException(String.format(
        "Can't construct %s object. Reason: expected that only gradle or intellij entity is null, actual: gradle='%s'; intellij='%s'",
        getClass(), gradleEntity, intellijEntity
      ));
    }
    myGradleEntity = gradleEntity;
    myIntellijEntity = intellijEntity;
  }

  @Nullable
  public G getGradleEntity() {
    return myGradleEntity;
  }
  
  @Nullable
  public I getIntellijEntity() {
    return myIntellijEntity;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myGradleEntity != null ? myGradleEntity.hashCode() : 0);
    return 31 * result + (myIntellijEntity != null ? myIntellijEntity.hashCode() : 0);
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    GradleEntityPresenceChange that = (GradleEntityPresenceChange)o;

    if (myGradleEntity != null ? !myGradleEntity.equals(that.myGradleEntity) : that.myGradleEntity != null) return false;
    if (myIntellijEntity != null ? !myIntellijEntity.equals(that.myIntellijEntity) : that.myIntellijEntity != null) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("entity presence change: gradle='%s', intellij='%s'", myGradleEntity, myIntellijEntity);
  }
}
