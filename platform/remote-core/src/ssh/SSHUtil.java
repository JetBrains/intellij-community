// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ssh;

import org.jetbrains.annotations.NonNls;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SSHUtil {
  public static final @NonNls Pattern PASSPHRASE_PROMPT = Pattern.compile("\\r?Enter passphrase for( key)? '(?<keyfile>.*)':\\s?");
  public static final @NonNls Pattern PASSWORD_PROMPT = Pattern.compile("(?<username>.*)'s password:\\s?");
  public static final @NonNls String PASSWORD_PROMPT_PREFIX = "password for";
  public static final @NonNls String PASSWORD_PROMPT_SUFFIX = "password:";
  public static final @NonNls String CONFIRM_CONNECTION_PROMPT = "Are you sure you want to continue connecting";
  public static final @NonNls String REMOTE_HOST_IDENTIFICATION_HAS_CHANGED = "remote host identification has changed";

  public static String extractKeyPath(Matcher matcher) {
    return matcher.group("keyfile");
  }

  public static String extractUsername(Matcher matcher) {
    return matcher.group("username");
  }
}
