/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks;

import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

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

  public static List<BranchInfo> fromTaskInfo(final VcsTaskHandler.TaskInfo taskInfo, final boolean original) {
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
