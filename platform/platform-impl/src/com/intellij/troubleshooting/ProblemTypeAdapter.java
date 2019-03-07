// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.troubleshooting;

import com.intellij.openapi.project.Project;
import com.intellij.settingsSummary.ProblemType;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("deprecation")
public class ProblemTypeAdapter implements TroubleInfoCollector {

  private final ProblemType myProblemType;

  public ProblemTypeAdapter(ProblemType problemType) {
    myProblemType = problemType;
  }

  @NotNull
  @Override
  public String collectInfo(@NotNull Project project) {
    return myProblemType.collectInfo(project);
  }
}
