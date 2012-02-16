package org.jetbrains.plugins.gradle.model.gradle;

import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;

/**
 * Stands for the entity from the 'import from gradle' domain.
 * <p/>
 * It's assumed to be safe to use implementations of this interface at hash-based containers (i.e. they are expected to correctly 
 * override {@link #equals(Object)} and {@link #hashCode()}.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 12:50 PM
 */
public interface GradleEntity extends Serializable {

  /**
   * Follows contract of {@link PropertyChangeSupport#addPropertyChangeListener(PropertyChangeListener)}  
   * 
   * @param listener  target listener
   */
  void addPropertyChangeListener(@NotNull PropertyChangeListener listener);
  
  void invite(@NotNull GradleEntityVisitor visitor);
  
  @NotNull
  GradleEntity clone(@NotNull GradleEntityCloneContext context);
}
