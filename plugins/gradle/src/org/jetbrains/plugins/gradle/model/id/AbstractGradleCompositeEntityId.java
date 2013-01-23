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
package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;

/**
 * There is a possible case that we need to work with an entity which references both gradle and ide entities. For example,
 * there is a possible case that particular library is outdated (gradle and ide are configured to use different versions of it).
 * We need to have an id entity which references both of them then. This class serves that purposes.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/22/13 5:17 PM
 */
public abstract class AbstractGradleCompositeEntityId<T extends GradleEntityId> extends AbstractGradleEntityId {

  @NotNull private final T myBaseId;
  @NotNull private final T myCounterPartyId;

  public AbstractGradleCompositeEntityId(@NotNull GradleEntityType type,
                                         @NotNull GradleEntityOwner baseOwner,
                                         @NotNull T baseId,
                                         @NotNull T counterPartyId)
  {
    super(type, baseOwner);
    myBaseId = baseId;
    myCounterPartyId = counterPartyId;
  }

  @NotNull
  protected T getBaseId() {
    return myBaseId;
  }

  @NotNull
  protected T getCounterPartyId() {
    return myCounterPartyId;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myBaseId.hashCode();
    result = 31 * result + myCounterPartyId.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    AbstractGradleCompositeEntityId id = (AbstractGradleCompositeEntityId)o;

    if (!myBaseId.equals(id.myBaseId)) return false;
    if (!myCounterPartyId.equals(id.myCounterPartyId)) return false;

    return true;
  }
}
