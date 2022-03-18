// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;

import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static com.intellij.util.BitUtil.isSet;

@ApiStatus.Internal
public final class PosixFilePermissionsUtil {
  private PosixFilePermissionsUtil() { }

  @SuppressWarnings("OctalInteger")
  public static Set<PosixFilePermission> fromMode(int unixMode) {
    Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
    if (isSet(unixMode, 0400)) permissions.add(PosixFilePermission.OWNER_READ);
    if (isSet(unixMode, 0200)) permissions.add(PosixFilePermission.OWNER_WRITE);
    if (isSet(unixMode, 0100)) permissions.add(PosixFilePermission.OWNER_EXECUTE);
    if (isSet(unixMode, 040)) permissions.add(PosixFilePermission.GROUP_READ);
    if (isSet(unixMode, 020)) permissions.add(PosixFilePermission.GROUP_WRITE);
    if (isSet(unixMode, 010)) permissions.add(PosixFilePermission.GROUP_EXECUTE);
    if (isSet(unixMode, 04)) permissions.add(PosixFilePermission.OTHERS_READ);
    if (isSet(unixMode, 02)) permissions.add(PosixFilePermission.OTHERS_WRITE);
    if (isSet(unixMode, 01)) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
    return permissions;
  }
}
