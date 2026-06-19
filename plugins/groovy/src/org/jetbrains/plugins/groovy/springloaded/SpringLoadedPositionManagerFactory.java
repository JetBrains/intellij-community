// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.springloaded;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerFactory;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for position manager to debug classes reloaded by com.springsource.springloaded
 */
public final class SpringLoadedPositionManagerFactory extends PositionManagerFactory {

  public static final Key<Boolean> FORCE_SPRINGLOADED = Key.create("springloaded.debugger.force");

  private static final Key<CachedValue<Boolean>> SPRING_LOADED_IN_PROJECT_CACHE_KEY = Key.create("springloaded.debugger.in.project.cache");

  @Override
  public PositionManager createPositionManager(final @NotNull DebugProcess process) {
    return usesSpringLoaded(process) ? new SpringLoadedPositionManager(process) : null;
  }

  private static boolean usesSpringLoaded(final @NotNull DebugProcess process) {
    Boolean force = process.getProcessHandler().getUserData(FORCE_SPRINGLOADED);
    if (force == Boolean.TRUE) return true;

    Project project = process.getProject();
    boolean isSpringSourceLoaded = CachedValuesManager.getManager(project).getCachedValue(
      project,
      SPRING_LOADED_IN_PROJECT_CACHE_KEY,
      () -> {
        boolean result = ReadAction.nonBlocking(() -> {
          if (DumbService.getInstance(project).isDumb()) return false;
          JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
          return facade.findPackage("com.springsource.loaded") != null ||
                 facade.findPackage("org.springsource.loaded") != null;
        }).executeSynchronously();

        return CachedValueProvider.Result.create(
          result,
          ProjectRootModificationTracker.getInstance(project),
          DumbService.getInstance(project).getModificationTracker()
        );
      },
      false
    );

    if (isSpringSourceLoaded) {
      return true;
    }

    // Check spring loaded for remote process
    VirtualMachineProxy proxy = VirtualMachineProxy.getCurrent();
    return !proxy.classesByName("com.springsource.loaded.agent.SpringLoadedAgent").isEmpty() ||
           !proxy.classesByName("org.springsource.loaded.agent.SpringLoadedAgent").isEmpty();
  }
}
