/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.cvsSupport2.connections;

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
    return new RootFormatter<CvsConnectionSettings>(this).createConfiguration(myCvsRootConfiguration.getCvsRootAsString());
  }

  public CvsConnectionSettings createSettings(final CvsMethod method, final String cvsRootAsString) {
    if (method.equals(CvsMethod.LOCAL_METHOD)) {
      return new LocalConnectionSettings(cvsRootAsString, myCvsRootConfiguration);
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

    throw new RuntimeException(com.intellij.CvsBundle.message("exception.text.unsupported.method", method));
  }

  public String getPServerPassword(final String cvsRoot) {
    return PServerLoginProvider.getInstance().getScrambledPasswordForCvsRoot(cvsRoot);
  }


}
