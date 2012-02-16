package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/14/12 1:32 PM
 */
public abstract class GradleAbstractEntityId implements GradleEntityId {

  @NotNull private final AtomicReference<GradleEntityOwner> myOwner = new AtomicReference<GradleEntityOwner>();
  @NotNull private final GradleEntityType myType;
  
  public GradleAbstractEntityId(@NotNull GradleEntityType type, @NotNull GradleEntityOwner owner) {
    myType = type;
    myOwner.set(owner);
  }
  
  @Override
  @NotNull
  public GradleEntityType getType() {
    return myType;
  }

  @Override
  @NotNull
  public GradleEntityOwner getOwner() {
    return myOwner.get();
  }

  @Override
  public void setOwner(@NotNull GradleEntityOwner owner) {
    myOwner.set(owner);
  }

  @Override
  public int hashCode() {
    return myType.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleAbstractEntityId that = (GradleAbstractEntityId)o;
    return myType.equals(that.myType);
  }
}
