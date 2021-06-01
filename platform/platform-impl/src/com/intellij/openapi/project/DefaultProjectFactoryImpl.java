// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;


public class DefaultProjectFactoryImpl extends DefaultProjectFactory {
  @Override
  public Project getDefaultProject() {
    return ProjectManager.getInstance().getDefaultProject();
  }
}
