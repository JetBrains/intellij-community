/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.connections.pserver.PServerLoginProvider;

public class CvsRootDataBuilder implements CvsRootSettingsBuilder<CvsRootData>{

  public static CvsRootData createSettingsOn(String cvsRoot, boolean check) {
    return new RootFormatter<CvsRootData>(new CvsRootDataBuilder()).createConfiguration(cvsRoot, check);
  }


  public CvsRootData createSettings(final CvsMethod method, final String cvsRootAsString) {
    final CvsRootData result = new CvsRootData(cvsRootAsString);
    result.METHOD = method;
    return result;
  }

  public String getPServerPassword(final String cvsRoot) {
    return PServerLoginProvider.getInstance().getScrambledPasswordForCvsRoot(cvsRoot);
  }
}
