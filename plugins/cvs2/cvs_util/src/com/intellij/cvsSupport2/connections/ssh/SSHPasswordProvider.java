/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.cvsSupport2.connections.ssh;

import org.jetbrains.annotations.Nullable;

public interface SSHPasswordProvider {
  @Nullable
  String getPasswordForCvsRoot(String cvsRoot);

  @Nullable
  String getPPKPasswordForCvsRoot(String cvsRoot);
}
