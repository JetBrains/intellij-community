// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;

public class ReplacementContext {

  private final ReplaceOptions options;
  private final Project project;

  ReplacementContext(ReplaceOptions _options, Project _project) {
    options = _options;
    project = _project;
  }

  public ReplaceOptions getOptions() {
    return options;
  }

  public Project getProject() {
    return project;
  }
}