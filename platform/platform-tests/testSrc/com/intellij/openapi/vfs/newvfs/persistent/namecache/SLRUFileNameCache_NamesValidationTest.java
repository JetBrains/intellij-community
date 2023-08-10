// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.namecache;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.fail;

public class SLRUFileNameCache_NamesValidationTest {
  //TODO RC: those verification rules are very wierd, they seems to be just cherry-picked to solve
  //         specific problems. We should either abandon verification altogether, or formulate simple
  //         and consistent rules.

  @ParameterizedTest
  @ValueSource(strings = {
    "usr",
    "bin",
    "Guest",
    "main.java",
    "archive-file.zip",
    "jdK_src.jar",
    "file$another",

    "digits1234567890",
    //we do not check 'valid filenames', we just check _paths_ are not enumerated by _name_ enumerator
    "symbols_-~$:;'`?~!@#%&*()+=|{}[]",
  })
  public void nameValid_WithoutFileSeparator_ExceptForTheStart(String nameWithoutSeparator) {
    assertNameValid(nameWithoutSeparator);
    assertNameValid('/' + nameWithoutSeparator);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "usr/",
    "usr/bin",

    "usr/../",
    "../usr",

    "./usr",
    "usr/.",

    "http://anything",
    "jar://junit.jar/!"
  })
  public void nameInvalid_WithFileSeparatorAnywhereButAtBeginning(String nameWithSeparator) {
    assertNameInvalid(nameWithSeparator);
    if(SystemInfo.isWindows) {
      //try both kinds of file-separator:
      assertNameInvalid(nameWithSeparator.replace('/', '\\'));
    }
  }

  //below are the exceptions: special kind of paths we allow to enumerate:

  @ParameterizedTest
  @ValueSource(strings = {
    "//wsl",
    "//wsl/",
    "//wsl/Ubuntu",
    "//wsl$/Ubuntu"
  })
  public void shortUNCPath_IsValidOnWindows_ButInvalidEverywhereElse(String uncPath) {
    if (SystemInfo.isWindows) {
      assertNameValid(uncPath);
    }
    else {
      assertNameInvalid(uncPath);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "http://",
    "jar://"
  })
  public void nameValid_ifEndsWithUrlSchema(String longUNCPath) {
    assertNameValid(longUNCPath);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "//wsl/Ubuntu/bin",
    "//wsl/Ubuntu/var/log"
  })
  public void nameInvalid_ifLongUNCName(String longUNCPath) {
    assertNameInvalid(longUNCPath);
  }


  //===========infra: ========================================

  private static void assertNameValid(@NotNull String name) {
    SLRUFileNameCache.assertShortFileName(name);
  }

  private static void assertNameInvalid(@NotNull String name) {
    try {
      SLRUFileNameCache.assertShortFileName(name);
      fail("Name [" + name + "] is invalid, must throw exception");
    }
    catch (IllegalArgumentException expected) {
    }
  }
}
