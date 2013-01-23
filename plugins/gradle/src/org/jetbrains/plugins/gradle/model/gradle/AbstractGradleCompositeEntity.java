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
package org.jetbrains.plugins.gradle.model.gradle;

import org.jetbrains.annotations.NotNull;

/**
 * There is a possible case that the same entity (e.g. library or jar) has different versions at gradle and ide side.
 * We treat that not as two entities (gradle- and ide-local) but as a single composite entity. This class is a base class
 * for such types of entities.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/23/13 9:19 AM
 */
public abstract class AbstractGradleCompositeEntity<G extends GradleEntity, I> extends AbstractGradleEntity {
  
  @NotNull private final G myGradleEntity;
  @NotNull private final I myIdeEntity;

  public AbstractGradleCompositeEntity(@NotNull G entity, @NotNull I entity1) {
    myGradleEntity = entity;
    myIdeEntity = entity1;
  }

  @NotNull
  public G getGradleEntity() {
    return myGradleEntity;
  }

  @NotNull
  public I getIdeEntity() {
    return myIdeEntity;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myGradleEntity.hashCode();
    result = 31 * result + myIdeEntity.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    AbstractGradleCompositeEntity entity = (AbstractGradleCompositeEntity)o;

    if (!myGradleEntity.equals(entity.myGradleEntity)) return false;
    if (!myIdeEntity.equals(entity.myIdeEntity)) return false;

    return true;
  }
}
