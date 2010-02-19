package com.intellij.appengine.gwt;

import com.intellij.appengine.server.integration.AppEngineServerIntegration;
import com.intellij.gwt.run.GwtDevModeServer;
import com.intellij.gwt.run.GwtDevModeServerProvider;
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.javaee.serverInstances.ApplicationServersManager;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineGwtServerProvider extends GwtDevModeServerProvider {
  @Override
  public List<? extends GwtDevModeServer> getServers() {
    final List<ApplicationServer> servers = ApplicationServersManager.getInstance().getApplicationServers(AppEngineServerIntegration.getInstance());
    final List<GwtDevModeServer> result = new ArrayList<GwtDevModeServer>();
    for (ApplicationServer server : servers) {
      result.add(new AppEngineGwtServer(server));
    }
    return result;
  }
}
