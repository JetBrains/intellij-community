package org.jetbrains.plugins.gradle.model.gradle;

import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * @author Denis Zhdanov
 * @since 8/25/11 3:44 PM
 */
public abstract class AbstractGradleEntity implements GradleEntity {

  private static final long serialVersionUID = 1L;
  
  private transient PropertyChangeSupport myPropertyChangeSupport;

  public AbstractGradleEntity() {
    myPropertyChangeSupport = new PropertyChangeSupport(this);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }
  
  protected void firePropertyChange(@NotNull String propertyName, @NotNull Object oldValue, @NotNull Object newValue) {
    myPropertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    myPropertyChangeSupport = new PropertyChangeSupport(this);
  }
  
  @Override
  public int hashCode() {
    // !!!! Change this implementation if current class has state. !!!
    return 1;
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object obj) {
    // !!!! Change this implementation if current class has state. !!!
    return true;
  }
} 