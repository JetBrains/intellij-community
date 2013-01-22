package org.jetbrains.plugins.groovy.springloaded;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerFactory;
import com.intellij.debugger.engine.DebugProcess;

/**
 * Factory for position manager to debug classes reloaded by com.springsource.springloaded
 * @author Sergey Evdokimov
 */
public class SpringLoadedPositionManagerFactory extends PositionManagerFactory {

  @Override
  public PositionManager createPositionManager(final DebugProcess process) {
    try {
      if (process.getVirtualMachineProxy().classesByName("com.springsource.loaded.agent.SpringLoadedAgent").size() > 0
          || process.getVirtualMachineProxy().classesByName("org.springsource.loaded.agent.SpringLoadedAgent").size() > 0) {
        return new SpringLoadedPositionManager(process);
      }
    }
    catch (Exception ignored) {
      // Some problem with virtual machine.
    }

    return null;
  }
}
