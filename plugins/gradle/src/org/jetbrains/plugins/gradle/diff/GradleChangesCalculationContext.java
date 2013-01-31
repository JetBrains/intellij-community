package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.util.GradleLibraryPathTypeMapper;

import java.util.HashSet;
import java.util.Set;

/**
 * <code>'Parameter object'</code> to use during project structure changes calculations.
 * <p/>
 * Thread-safe.
 */
public class GradleChangesCalculationContext {

  @NotNull private final Set<GradleProjectStructureChange> myKnownChanges   = new HashSet<GradleProjectStructureChange>();
  @NotNull private final Set<GradleProjectStructureChange> myCurrentChanges = new HashSet<GradleProjectStructureChange>();

  @NotNull private final PlatformFacade              myPlatformFacade;
  @NotNull private final GradleLibraryPathTypeMapper myLibraryPathTypeMapper;

  /**
   * @param knownChanges    changes between the gradle and ide project structure that has been known up until now
   * @param platformFacade  platform facade to use during the calculations
   * @param mapper          library path type mapper to use during the calculation
   */
  public GradleChangesCalculationContext(@NotNull Set<GradleProjectStructureChange> knownChanges,
                                         @NotNull PlatformFacade platformFacade,
                                         @NotNull GradleLibraryPathTypeMapper mapper)
  {
    myLibraryPathTypeMapper = mapper;
    myKnownChanges.addAll(knownChanges);
    myPlatformFacade = platformFacade;
  }
  
  @NotNull
  public Set<GradleProjectStructureChange> getKnownChanges() {
    return myKnownChanges;
  }

  @NotNull
  public Set<GradleProjectStructureChange> getCurrentChanges() {
    return myCurrentChanges;
  }
  
  public void register(@NotNull GradleProjectStructureChange change) {
    myCurrentChanges.add(change);
  }

  public boolean hasNewChanges() {
    return !myKnownChanges.equals(myCurrentChanges);
  }
  
  @NotNull
  public PlatformFacade getPlatformFacade() {
    return myPlatformFacade;
  }

  @NotNull
  public GradleLibraryPathTypeMapper getLibraryPathTypeMapper() {
    return myLibraryPathTypeMapper;
  }
}
