/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.configuration;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import org.jetbrains.annotations.NotNull;

public class RemoteServerSearchableOptionContributor extends SearchableOptionContributor {

  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    for (ServerType<?> serverType : ServerType.EP_NAME.getExtensions()) {
      String typeName = serverType.getPresentableName();
      processor.addOptions(typeName, null, typeName + " cloud type", RemoteServerListConfigurable.ID, null, false);
      for (RemoteServer<?> server : RemoteServersManager.getInstance().getServers(serverType)) {
        String serverName = server.getName();
        processor.addOptions(serverName, null, serverName + " cloud instance", RemoteServerListConfigurable.ID, null, false);
      }
    }
  }
}
