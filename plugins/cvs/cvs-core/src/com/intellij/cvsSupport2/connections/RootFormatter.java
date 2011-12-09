/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.connections;

import com.intellij.CvsBundle;
import com.intellij.openapi.util.text.StringUtil;
import org.netbeans.lib.cvsclient.connection.PServerPasswordScrambler;

/**
 * author: lesya
 */

public class RootFormatter<Settings extends CvsSettings> {
  private final CvsRootSettingsBuilder<Settings> myBuilder;

  public RootFormatter(CvsRootSettingsBuilder<Settings> builder) {
    myBuilder = builder; 
  }

  public Settings createConfiguration(String rootAsString, boolean check) {
    final CvsRootParser root = CvsRootParser.valueOf(rootAsString, check);
    final Settings result = myBuilder.createSettings(root.METHOD, rootAsString);
    if (CvsMethod.LOCAL_METHOD.equals(root.METHOD)) {
      fillLocalSettings(root.REPOSITORY, result);
    }
    else if (CvsMethod.PSERVER_METHOD.equals(root.METHOD)) {
      fillPServerSettings(root, result, rootAsString);
    }
    else if (CvsMethod.EXT_METHOD.equals(root.METHOD) || CvsMethod.SSH_METHOD.equals(root.METHOD)) {
      fillSettings(root, result);
    }
    else {
      throw new CvsRootException(CvsBundle.message("exception.text.unsupported.method", root.METHOD, rootAsString));
    }
    return result;
  }

  private void fillLocalSettings(String repository, Settings settings) {
    settings.setRepository(repository);
  }

  private void fillPServerSettings(CvsRootParser root, Settings settings, final String rootAsString) {
    fillSettings(root, settings);
    if (root.PASSWORD != null) {
      settings.setPassword(root.PASSWORD);
    } else {
      settings.setPassword(getPServerConnectionPassword(rootAsString, root));
    }
  }

  private void fillSettings(CvsRootParser root, Settings settings) {
    final String[] hostAndPort = root.HOST.split(":");
    settings.setHost(hostAndPort[0]);
    if (hostAndPort.length > 1) {
      settings.setPort(Integer.parseInt(hostAndPort[1]));
    }
    else if (root.PORT != null) {
      try {
        settings.setPort(Integer.parseInt(root.PORT));
      }
      catch (NumberFormatException e) {
        settings.setHost(hostAndPort[0]);
      }
    }
    else {
      settings.setHost(hostAndPort[0]);
    }

    String repository = root.REPOSITORY;
    if (StringUtil.startsWithChar(repository, ':')) {
      repository = repository.substring(1);
    }
    settings.setRepository(repository);
    final String[] userAndPassword = root.USER_NAME.split(":");
    settings.setUser(userAndPassword[0]);

    if (root.PROXY_HOST != null) {
      settings.setUseProxy(root.PROXY_HOST, root.PROXY_PORT);
    }    
  }

  private String getPServerConnectionPassword(String cvsRoot, CvsRootParser root) {
    final String[] userAndPassword = root.USER_NAME.split(":");
    if (userAndPassword.length > 1) {
      return PServerPasswordScrambler.getInstance().scramble(userAndPassword[1]);
    }
    else {
      return myBuilder.getPServerPassword(cvsRoot);
    }
  }
}
