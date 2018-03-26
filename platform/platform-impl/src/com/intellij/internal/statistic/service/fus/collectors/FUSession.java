// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class FUSession {
  public final static FUSession APPLICATION_SESSION = create("application_session");

  private final int id;
  private String buildId;

  public static FUSession create(@NotNull String sessionId) {
    return new FUSession(sessionId);
  }

  public static FUSession create(@NotNull Project project) {
    return new FUSession(ProjectUtil.getProjectCacheFileName(project, false, "." ));
  }

  public FUSession(@NotNull String id) {
    this.id = id.hashCode();
    this.buildId = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCodeAndSnapshot();
    if (buildId.endsWith(".")) buildId += "0";
  }

  public int getId() {
    return id;
  }

  public String getBuildId() {
    return buildId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FUSession)) return false;
    FUSession session = (FUSession)o;
    return Objects.equals(id, session.id) &&
           Objects.equals(buildId, session.buildId);
  }

  @Override
  public int hashCode() {

    return Objects.hash(id, buildId);
  }
}
