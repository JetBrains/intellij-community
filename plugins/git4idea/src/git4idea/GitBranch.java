package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * This data class represents a Git branch
 */
public class GitBranch {
  private final Project project;
  private final String name;
  private final boolean remote;
  private final boolean active;

  public GitBranch(@NotNull Project project, @NotNull String name, boolean active, boolean remote) {
    this.project = project;
    this.name = name;
    this.remote = remote;
    this.active = active;
  }

  @NotNull
  public Project getProject() {
    return project;
  }

  @NotNull
  public String getName() {
    return name;
  }

  public boolean isRemote() {
    return remote;
  }

  public boolean isActive() {
    return active;
  }
}
