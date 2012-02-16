package org.jetbrains.plugins.gradle.model.gradle;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/25/11 5:38 PM
 */
public abstract class AbstractNamedGradleEntity extends AbstractGradleEntity implements Named {

  private static final long serialVersionUID = 1L;
  
  private String myName;

  public AbstractNamedGradleEntity(@NotNull String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    String oldName = myName;
    myName = name;
    firePropertyChange(NAME_PROPERTY, oldName, name);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myName.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    AbstractNamedGradleEntity that = (AbstractNamedGradleEntity)o;
    return myName.equals(that.myName);
  }
}
