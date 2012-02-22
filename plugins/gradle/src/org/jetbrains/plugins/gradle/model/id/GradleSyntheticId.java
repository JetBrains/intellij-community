package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

/**
 * Whole 'entity id' thing is necessary for mapping 'sync project structures' tree nodes to the domain entities. However, there are
 * special types of nodes that don't map to any entity - {@link GradleEntityType#SYNTHETIC synthetic nodes}.
 * <p/>
 * This class is designed to serve as a data object for such a nodes.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/16/12 11:40 AM
 */
public class GradleSyntheticId extends GradleAbstractEntityId {
  
  @NotNull private final String myText;

  public GradleSyntheticId(@NotNull String text) {
    super(GradleEntityType.SYNTHETIC, GradleEntityOwner.GRADLE/* no matter what owner is used */);
    myText = text;
  }

  @Override
  public String toString() {
    return myText;
  }

  @Override
  public Object mapToEntity(@NotNull GradleProjectStructureContext context) {
    return null;
  }
}
