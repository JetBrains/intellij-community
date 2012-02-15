package org.jetbrains.plugins.gradle.model.gradle;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:41 PM
 */
public abstract class AbstractGradleDependency<T extends AbstractGradleEntity & Named> extends AbstractGradleEntity implements GradleDependency, Named {

  private static final long serialVersionUID = 1L;

  private final GradleModule myOwnerModule;
  private final T            myTarget;
  
  private DependencyScope myScope = DependencyScope.COMPILE;

  private transient boolean mySkipNameChange;
  private           boolean myExported;

  protected AbstractGradleDependency(@NotNull GradleModule ownerModule, @NotNull T dependency) {
    myOwnerModule = ownerModule;
    myTarget = dependency;
    initListener();
  }

  @NotNull
  public GradleModule getOwnerModule() {
    return myOwnerModule;
  }

  @NotNull
  public T getTarget() {
    return myTarget;
  }
  
  @Override
  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  public void setScope(DependencyScope scope) {
    myScope = scope;
  }

  @Override
  public boolean isExported() {
    return myExported;
  }

  public void setExported(boolean exported) {
    myExported = exported;
  }

  @NotNull
  @Override
  public String getName() {
    return myTarget.getName();
  }
  
  @Override
  public void setName(@NotNull String name) {
    mySkipNameChange = true;
    try {
      String oldName = myTarget.getName();
      myTarget.setName(name);
      firePropertyChange(NAME_PROPERTY, oldName, name);
    }
    finally {
      mySkipNameChange = false;
    }
  }

  private void initListener() {
    myTarget.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (mySkipNameChange || !NAME_PROPERTY.equals(evt.getPropertyName())) {
          return;
        }
        mySkipNameChange = true;
        try {
          firePropertyChange(NAME_PROPERTY, evt.getOldValue(), evt.getNewValue());
        }
        finally {
          mySkipNameChange = false;
        }
      }
    });
  }

  @SuppressWarnings("MethodOverridesPrivateMethodOfSuperclass")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    initListener();
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myOwnerModule.hashCode();
    result = 31 * result + myTarget.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractGradleDependency<?> that = (AbstractGradleDependency<?>)o;
    return myOwnerModule.equals(that.myOwnerModule) && myTarget.equals(that.myTarget);
  }

  @Override
  public String toString() {
    return "dependency=" + getTarget() + "|scope=" + getScope() + "|exported=" + isExported() + "|owner=" + getOwnerModule();
  }
  
  @NotNull
  @Override
  public GradleDependency clone() {
    try {
      return (GradleDependency)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  protected void copyTo(@NotNull AbstractGradleDependency that) {
    that.setExported(isExported());
    that.setScope(getScope());
  }
}
