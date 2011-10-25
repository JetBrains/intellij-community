package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:46 PM
 */
public class GradleLibraryDependency extends AbstractGradleDependency implements Named {

  private final GradleLibrary myLibrary;
  private transient boolean mySkipNameChange;

  public GradleLibraryDependency(@NotNull GradleLibrary library) {
    myLibrary = library;
    initListener();
  }

  @NotNull
  public GradleLibrary getLibrary() {
    return myLibrary;
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this); 
  }

  @NotNull
  @Override
  public String getName() {
    return myLibrary.getName();
  }

  @Override
  public void setName(@NotNull String name) {
    mySkipNameChange = true;
    try {
      String oldName = myLibrary.getName();
      myLibrary.setName(name);
      firePropertyChange(Named.NAME_PROPERTY, oldName, name);
    }
    finally {
      mySkipNameChange = false;
    }
  }

  @SuppressWarnings("MethodOverridesPrivateMethodOfSuperclass")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    initListener();
  }

  private void initListener() {
    myLibrary.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (mySkipNameChange || !Named.NAME_PROPERTY.equals(evt.getPropertyName())) {
          return;
        }
        mySkipNameChange = true;
        try {
          firePropertyChange(Named.NAME_PROPERTY, evt.getOldValue(), evt.getNewValue());
        }
        finally {
          mySkipNameChange = false;
        }
      }
    });
  }
  
  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myLibrary.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    GradleLibraryDependency that = (GradleLibraryDependency)o;
    return myLibrary.equals(that.myLibrary);
  }

  @Override
  public String toString() {
    return super.toString() + ", dependency library: " + getLibrary();
  }

  @NotNull
  @Override
  public GradleLibraryDependency clone(@NotNull GradleEntityCloneContext context) {
    GradleLibraryDependency result = new GradleLibraryDependency(getLibrary().clone(context));
    copyTo(result);
    return result;
  }
}
