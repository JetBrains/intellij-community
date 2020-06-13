// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ssh;

import java.util.regex.Pattern;

public final class SSHUtil {
  public static final Pattern PASSPHRASE_PROMPT = Pattern.compile("Enter passphrase for key \\'(.*)\\':\\s?");
  public static final Pattern PASSWORD_PROMPT = Pattern.compile("(.*)\\'s password:\\s?");
  public static final String PASSWORD_PROMPT_SUFFIX = "password:";
  public static final String CONFIRM_CONNECTION_PROMPT = "Are you sure you want to continue connecting";
  public static final String REMOTE_HOST_IDENTIFICATION_HAS_CHANGED = "remote host identification has changed";
}
