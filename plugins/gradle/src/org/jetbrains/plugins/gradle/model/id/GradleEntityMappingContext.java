package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;

/**
 * Facades all services necessary for the {@link GradleEntityId#mapToEntity(GradleEntityMappingContext) 'entity id -&gt; entity}
 * mapping.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/14/12 1:26 PM
 */
public class GradleEntityMappingContext {

  @NotNull private final GradleProjectStructureHelper       myProjectStructureHelper;
  @NotNull private final GradleProjectStructureChangesModel myChangesModel;

  public GradleEntityMappingContext(GradleProjectStructureHelper projectStructureHelper, GradleProjectStructureChangesModel changesModel) {
    myProjectStructureHelper = projectStructureHelper;
    myChangesModel = changesModel;
  }

  @NotNull
  public GradleProjectStructureHelper getProjectStructureHelper() {
    return myProjectStructureHelper;
  }

  @NotNull
  public GradleProjectStructureChangesModel getChangesModel() {
    return myChangesModel;
  }
}
