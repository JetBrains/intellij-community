// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl.api;

import com.intellij.openapi.components.ServiceManager;

/*
 * the purpose of this class - to decouple google AS error reporting from Gradle DSL
 */
public interface BuildModelErrorReporter {
  void report(Throwable e);

  static BuildModelErrorReporter getInstance() {return ServiceManager.getService(BuildModelErrorReporter.class);}


  class Dummy implements BuildModelErrorReporter{
    @Override
    public void report(Throwable e) {}
  }
}
