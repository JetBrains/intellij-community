// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.springloaded;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerFactory;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for position manager to debug classes reloaded by com.springsource.springloaded
 */
public final class SpringLoadedPositionManagerFactory extends PositionManagerFactory {

  public static final Key<Boolean> FORCE_SPRINGLOADED = Key.create("springloaded.debugger.force");

  @Override
  public PositionManager createPositionManager(final @NotNull DebugProcess process) {
    return usesSpringLoaded(process) ? new SpringLoadedPositionManager(process) : null;
  }

  private static boolean usesSpringLoaded(final @NotNull DebugProcess process) {
    Boolean force = process.getProcessHandler().getUserData(FORCE_SPRINGLOADED);
    if (force == Boolean.TRUE) return true;

    if (ReadAction.compute(()->{
      JavaPsiFacade facade = JavaPsiFacade.getInstance(process.getProject());
      if (facade.findPackage("com.springsource.loaded") != null ||
          facade.findPackage("org.springsource.loaded") != null) {
        return true;
      }
      return false;
    })) {
      return true;
    }

    // Check spring loaded for remote process
    VirtualMachineProxy proxy = process.getVirtualMachineProxy();
    return !proxy.classesByName("com.springsource.loaded.agent.SpringLoadedAgent").isEmpty() ||
           !proxy.classesByName("org.springsource.loaded.agent.SpringLoadedAgent").isEmpty();
  }
}
