package org.jetbrains.plugins.groovy.springloaded;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerFactory;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.JavaPsiFacade;

/**
 * Factory for position manager to debug classes reloaded by com.springsource.springloaded
 * @author Sergey Evdokimov
 */
public class SpringLoadedPositionManagerFactory extends PositionManagerFactory {

  @Override
  public PositionManager createPositionManager(final DebugProcess process) {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(process.getProject());
      if (facade.findPackage("com.springsource.loaded") != null || facade.findPackage("org.springsource.loaded") != null) {
        return new SpringLoadedPositionManager(process);
      }
      return null;
    }
    finally {
      accessToken.finish();
    }
  }
}
