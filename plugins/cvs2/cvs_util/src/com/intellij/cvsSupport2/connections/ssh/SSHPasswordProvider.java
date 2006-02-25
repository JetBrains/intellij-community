/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.cvsSupport2.connections.ssh;

public interface SSHPasswordProvider {
  String getPasswordForCvsRoot(String cvsRoot);

  String getPPKPasswordForCvsRoot(String cvsRoot);
}
