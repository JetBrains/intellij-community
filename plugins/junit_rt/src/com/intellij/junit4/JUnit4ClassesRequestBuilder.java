// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.junit4;

import org.junit.runner.Request;

public final class JUnit4ClassesRequestBuilder {
  public static Request getClassesRequest(String suiteName, Class<?>[] classes) {
    try {
      return (Request)Class.forName("org.junit.internal.requests.ClassesRequest")
                    .getConstructor(new Class[]{String.class, Class[].class})
                    .newInstance(new Object[]{suiteName, classes});
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}