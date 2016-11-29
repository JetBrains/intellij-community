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
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.ext.ExtConnectionCvsSettings;
import com.intellij.cvsSupport2.connections.local.LocalConnectionSettings;
import com.intellij.cvsSupport2.connections.pserver.PServerCvsSettings;
import com.intellij.cvsSupport2.connections.pserver.PServerLoginProvider;
import com.intellij.cvsSupport2.connections.ssh.SshConnectionSettings;

public class IDEARootFormatter implements CvsRootSettingsBuilder<CvsConnectionSettings>{
  private final CvsRootConfiguration myCvsRootConfiguration;


  public IDEARootFormatter(final CvsRootConfiguration cvsRootConfiguration) {
    myCvsRootConfiguration = cvsRootConfiguration;
  }
  
  public CvsConnectionSettings createConfiguration() {
    return new RootFormatter<>(this).createConfiguration(myCvsRootConfiguration.getCvsRootAsString(), true);
  }

  @Override
  public CvsConnectionSettings createSettings(final CvsMethod method, final String cvsRootAsString) {
    if (method == null) {
      throw new CvsRootException(CvsBundle.message("message.error.missing.cvs.root", cvsRootAsString));
    }
    if (method.equals(CvsMethod.LOCAL_METHOD)) {
      return new LocalConnectionSettings(myCvsRootConfiguration);
    }
    else if (method.equals(CvsMethod.PSERVER_METHOD)) {
      return new PServerCvsSettings(myCvsRootConfiguration);
    }
    else if (method.equals(CvsMethod.EXT_METHOD)) {
      return new ExtConnectionCvsSettings(myCvsRootConfiguration);
    }
    else if (method.equals(CvsMethod.SSH_METHOD)) {
      return new SshConnectionSettings(myCvsRootConfiguration);
    }

    throw new CvsRootException(CvsBundle.message("exception.text.unsupported.method", method, cvsRootAsString));
  }

  @Override
  public String getPServerPassword(final String cvsRoot) {
    return PServerLoginProvider.getInstance().getScrambledPasswordForCvsRoot(cvsRoot);
  }
}
