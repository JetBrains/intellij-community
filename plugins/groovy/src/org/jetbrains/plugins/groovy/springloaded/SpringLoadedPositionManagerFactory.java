/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.springloaded;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerFactory;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.JavaPsiFacade;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for position manager to debug classes reloaded by com.springsource.springloaded
 * @author Sergey Evdokimov
 */
public class SpringLoadedPositionManagerFactory extends PositionManagerFactory {

  @Override
  public PositionManager createPositionManager(@NotNull final DebugProcess process) {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(process.getProject());
      if (facade.findPackage("com.springsource.loaded") != null || facade.findPackage("org.springsource.loaded") != null) {
        return new SpringLoadedPositionManager(process);
      }
    }
    finally {
      accessToken.finish();
    }

    try {
      // Check spring loaded for remote process
      if (!process.getVirtualMachineProxy().classesByName("com.springsource.loaded.agent.SpringLoadedAgent").isEmpty()
          || !process.getVirtualMachineProxy().classesByName("org.springsource.loaded.agent.SpringLoadedAgent").isEmpty()) {
        return new SpringLoadedPositionManager(process);
      }
    }
    catch (Exception ignored) {
      // Some problem with virtual machine.
    }

    return null;
  }
}
