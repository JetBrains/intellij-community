/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.cvsSupport2.connections;

public interface CvsRootSettingsBuilder <Settings extends CvsSettings>{
  Settings createSettings(CvsMethod method, final String cvsRootAsString);
  String getPServerPassword(final String cvsRoot);
}

