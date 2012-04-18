package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;

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

  @NotNull private final PlatformFacade myPlatformFacade;

  /**
   * @param knownChanges    changes between the gradle and intellij project structure that has been known up until now
   * @param platformFacade  platform facade to use during the calculations
   */
  public GradleChangesCalculationContext(@NotNull Set<GradleProjectStructureChange> knownChanges,
                                         @NotNull PlatformFacade platformFacade)
  {
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
}
