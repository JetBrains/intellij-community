// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

@ApiStatus.Internal
public final class PosixFilePermissionsUtil {
  @SuppressWarnings({"SpellCheckingInspection", "OctalInteger"}) private static final int S_IRUSR = 0400;
  @SuppressWarnings({"SpellCheckingInspection", "OctalInteger"}) private static final int S_IWUSR = 0200;
  @SuppressWarnings({"SpellCheckingInspection", "OctalInteger"}) private static final int S_IXUSR = 0100;
  @SuppressWarnings({"SpellCheckingInspection", "OctalInteger"}) private static final int S_IRGRP = 0040;
  @SuppressWarnings({"SpellCheckingInspection", "OctalInteger"}) private static final int S_IWGRP = 0020;
  @SuppressWarnings({"SpellCheckingInspection", "OctalInteger"}) private static final int S_IXGRP = 0010;
  @SuppressWarnings({"SpellCheckingInspection", "OctalInteger"}) private static final int S_IROTH = 0004;
  @SuppressWarnings({"SpellCheckingInspection", "OctalInteger"}) private static final int S_IWOTH = 0002;
  @SuppressWarnings({"SpellCheckingInspection", "OctalInteger"}) private static final int S_IXOTH = 0001;

  private PosixFilePermissionsUtil() { }

  public static @NotNull Set<PosixFilePermission> fromUnixMode(int mode) {
    Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
    if ((mode & S_IRUSR) != 0) permissions.add(PosixFilePermission.OWNER_READ);
    if ((mode & S_IWUSR) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
    if ((mode & S_IXUSR) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);
    if ((mode & S_IRGRP) != 0) permissions.add(PosixFilePermission.GROUP_READ);
    if ((mode & S_IWGRP) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
    if ((mode & S_IXGRP) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);
    if ((mode & S_IROTH) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
    if ((mode & S_IWOTH) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
    if ((mode & S_IXOTH) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
    return permissions;
  }

  public static int toUnixMode(@NotNull Set<PosixFilePermission> permissions) {
    int mode = 0;
    if (permissions.contains(PosixFilePermission.OWNER_READ)) mode |= S_IRUSR;
    if (permissions.contains(PosixFilePermission.OWNER_WRITE)) mode |= S_IWUSR;
    if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) mode |= S_IXUSR;
    if (permissions.contains(PosixFilePermission.GROUP_READ)) mode |= S_IRGRP;
    if (permissions.contains(PosixFilePermission.GROUP_WRITE)) mode |= S_IWGRP;
    if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) mode |= S_IXGRP;
    if (permissions.contains(PosixFilePermission.OTHERS_READ)) mode |= S_IROTH;
    if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) mode |= S_IWOTH;
    if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) mode |= S_IXOTH;
    return mode;
  }
}
