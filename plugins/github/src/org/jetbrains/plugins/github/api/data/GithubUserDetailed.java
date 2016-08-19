/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api.data;

import com.intellij.tasks.impl.gson.Mandatory;
import com.intellij.tasks.impl.gson.RestModel;
import org.jetbrains.annotations.Nullable;

@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubUserDetailed extends GithubUser {
  private String name;
  private String email;
  private String company;
  private String location;
  private String type;

  private Integer publicRepos;
  private Integer publicGists;
  private Integer totalPrivateRepos;
  private Integer ownedPrivateRepos;
  private Integer privateGists;
  private Long diskUsage;

  private UserPlan plan;

  @RestModel
  public static class UserPlan {
    private String name;
    private Long space;
    private Long collaborators;
    @Mandatory private Long privateRepos;

    public long getPrivateRepos() {
      return privateRepos;
    }
  }

  @Nullable
  public String getName() {
    return name;
  }

  @Nullable
  public String getEmail() {
    return email;
  }

  public boolean canCreatePrivateRepo() {
    return plan == null || ownedPrivateRepos == null || plan.getPrivateRepos() > ownedPrivateRepos;
  }
}
