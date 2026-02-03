// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import org.junit.Assert;
import org.junit.Test;

public class DumpTestEnvironmentTest {
  @Test
  public void testSecretParameters() {
    Assert.assertTrue(isSecretParameter("bla-token"));
    Assert.assertTrue(isSecretParameter("bla.secret.param"));
    Assert.assertTrue(isSecretParameter("idea.pasSword"));
    Assert.assertTrue(isSecretParameter("npm.auth.KEY"));
  }

  private static boolean isSecretParameter(String name) {
    for (String word : name.split("\\W+")) {
      if (word.equalsIgnoreCase("secret") ||
          word.equalsIgnoreCase("token") ||
          word.equalsIgnoreCase("key") ||
          word.equalsIgnoreCase("password")) {
        return true;
      }
    }

    return false;
  }
}
