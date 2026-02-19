// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks;

import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
@Tag("branch")
public class BranchInfo {

  @Attribute("name")
  public String name;

  @Attribute("repository")
  public String repository;

  @Attribute("original")
  public boolean original;

  public static @Unmodifiable List<BranchInfo> fromTaskInfo(final VcsTaskHandler.TaskInfo taskInfo, final boolean original) {
    return ContainerUtil.map(taskInfo.getRepositories(), s -> {
      BranchInfo info = new BranchInfo();
      info.name = taskInfo.getName();
      info.repository = s;
      info.original = original;
      return info;
    });
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BranchInfo info = (BranchInfo)o;

    if (original != info.original) return false;
    if (name != null ? !name.equals(info.name) : info.name != null) return false;
    if (repository != null ? !repository.equals(info.repository) : info.repository != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (repository != null ? repository.hashCode() : 0);
    result = 31 * result + (original ? 1 : 0);
    return result;
  }
}
