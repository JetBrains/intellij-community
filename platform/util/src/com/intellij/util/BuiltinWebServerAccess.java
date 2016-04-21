/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

/**
 * Provides access to the token used to authenticate against the built in webserver.
 * The webserver uses basic auth in the form of:
 * username: "_token_"
 * password: the value returned by getUserAuthenticationToken()
 */
public class BuiltinWebServerAccess {
  private static final String TOKEN_FILE = "user.token";
  private static String ourUserAuthenticationToken = null;
  private static final Object LOCK = new Object();

  @NotNull
  public static String getUserAuthenticationToken() throws IOException {
    synchronized (LOCK) {
      if (ourUserAuthenticationToken != null) {
        return ourUserAuthenticationToken;
      }

      File tokenFile = getTokenFile();
      if (tokenFile.exists()) {
        ourUserAuthenticationToken = FileUtil.loadFile(tokenFile);
        return ourUserAuthenticationToken;
      }
      throw new IOException("User Authentication Token not found");
    }
  }

  private static File getTokenFile() {
    String configPath = PathManager.getConfigPath();
    File tokenFile = new File(configPath + File.separator + TOKEN_FILE);
    return tokenFile;
  }

  public static void ensureUserAuthenticationToken() throws NoSuchAlgorithmException, IOException {
    File tokenFile = getTokenFile();
    if (!tokenFile.exists()) {
      PathManager.ensureConfigFolderExists();
      byte[] seed = SecureRandom.getInstance("SHA1PRNG").generateSeed(24);
      BigInteger intSeed = new BigInteger(seed);
      synchronized(LOCK) {
        ourUserAuthenticationToken = intSeed.toString(36);
        FileUtil.writeToFile(tokenFile, ourUserAuthenticationToken);
      }
    }
  }
}
