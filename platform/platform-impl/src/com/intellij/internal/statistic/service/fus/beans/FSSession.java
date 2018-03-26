// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.beans;

import com.intellij.internal.statistic.service.fus.collectors.FUSession;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FSSession {
  public String id;
  public String build;

  public static FSSession create(@NotNull Project project) {
    return create(FUSession.create(project));
  }

  public static FSSession create(@NotNull FUSession session) {
    return new FSSession(Integer.toString(session.getId()), session.getBuildId());
  }

  private FSSession(String id, String build) {
    this.id = id;
    this.build = build;
  }

  public List<FSGroup> groups = null;

  public void addGroup(@NotNull FSGroup group) {
    if (groups == null) {
      groups = ContainerUtil.newArrayList();
    }
    groups.add(group);
  }

  @Nullable
  public List<FSGroup> getGroups() {
    return groups;
  }

  public boolean hasGroups() {
    return groups != null;
  }

  public void removeEmptyData() {
    if (groups != null) {
       groups = groups.stream().filter(group -> !group.getMetrics().isEmpty()).collect(Collectors.toList());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FSSession session = (FSSession)o;
    return Objects.equals(id, session.id) &&
           Objects.equals(build, session.build);
  }

  @Override
  public int hashCode() {

    return Objects.hash(id, build);
  }
}
