/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ssh;

import java.util.regex.Pattern;

public class SSHUtil {
  public static final Pattern PASSPHRASE_PROMPT = Pattern.compile("Enter passphrase for key \\'(.*)\\':\\s?");
  public static final Pattern PASSWORD_PROMPT = Pattern.compile("(.*)\\'s password:\\s?");
  public static final String PASSWORD_PROMPT_SUFFIX = "password:";
  public static final String CONFIRM_CONNECTION_PROMPT = "Are you sure you want to continue connecting";
  public static final String REMOTE_HOST_IDENTIFICATION_HAS_CHANGED = "remote host identification has changed";
}
