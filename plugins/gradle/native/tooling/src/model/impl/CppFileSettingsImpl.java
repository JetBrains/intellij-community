// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl;

import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppFileSettings;

import java.io.File;

public class CppFileSettingsImpl implements CppFileSettings {

  private File myObjectFile;

  public CppFileSettingsImpl() {
  }

  public CppFileSettingsImpl(CppFileSettings fileSettings) {
    myObjectFile = fileSettings.getObjectFile();
  }

  @Override
  public File getObjectFile() {
    return myObjectFile;
  }

  public void setObjectFile(File objectFile) {
    myObjectFile = objectFile;
  }
}
