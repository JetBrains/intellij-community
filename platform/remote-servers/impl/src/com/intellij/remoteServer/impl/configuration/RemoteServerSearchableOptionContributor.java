// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import org.jetbrains.annotations.NotNull;

final class RemoteServerSearchableOptionContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    for (ServerType<?> serverType : ServerType.EP_NAME.getExtensionList()) {
      String typeName = serverType.getPresentableName();
      processor.addOptions(typeName, null, typeName + " cloud type", RemoteServerListConfigurable.ID, null, false);
      for (RemoteServer<?> server : RemoteServersManager.getInstance().getServers(serverType)) {
        String serverName = server.getName();
        processor.addOptions(serverName, null, serverName + " cloud instance", RemoteServerListConfigurable.ID, null, false);
      }
    }
  }
}
