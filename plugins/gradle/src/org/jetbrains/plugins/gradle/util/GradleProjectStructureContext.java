package org.jetbrains.plugins.gradle.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;

/**
 * Facades all services necessary for the 'sync project changes' processing.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/14/12 1:26 PM
 */
public class GradleProjectStructureContext {

  @NotNull private final GradleProjectStructureHelper       myProjectStructureHelper;
  @NotNull private final PlatformFacade                     myPlatformFacade;
  @NotNull private final GradleProjectStructureChangesModel myChangesModel;

  public GradleProjectStructureContext(@NotNull GradleProjectStructureHelper projectStructureHelper,
                                       @NotNull PlatformFacade platformFacade,
                                       @NotNull GradleProjectStructureChangesModel changesModel)
  {
    myProjectStructureHelper = projectStructureHelper;
    myPlatformFacade = platformFacade;
    myChangesModel = changesModel;
  }

  @NotNull
  public GradleProjectStructureHelper getProjectStructureHelper() {
    return myProjectStructureHelper;
  }

  @NotNull
  public PlatformFacade getPlatformFacade() {
    return myPlatformFacade;
  }

  @NotNull
  public GradleProjectStructureChangesModel getChangesModel() {
    return myChangesModel;
  }
}
