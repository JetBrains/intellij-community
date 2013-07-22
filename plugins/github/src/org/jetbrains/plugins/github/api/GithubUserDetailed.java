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
package org.jetbrains.plugins.github.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubUserDetailed extends GithubUser {
  @Nullable private String name;
  @Nullable private String email;
  @Nullable private String company;
  @Nullable private String location;

  private int publicRepos;
  private int publicGists;
  private int totalPrivateRepos;
  private int ownedPrivateRepos;
  private int privateGists;
  private long diskUsage;

  @NotNull private String type;
  @NotNull private UserPlan plan;

  public static class UserPlan implements Serializable {
    @NotNull private String name;
    private long space;
    private long collaborators;
    private long privateRepos;

    @NotNull
    public static UserPlan create(@Nullable GithubUserRaw.GithubUserPlanRaw raw) throws JsonException {
      try {
        if (raw == null) throw new JsonException("raw is null");
        if (raw.name == null) throw new JsonException("name is null");
        if (raw.space == null) throw new JsonException("space is null");
        if (raw.collaborators == null) throw new JsonException("collaborators is null");
        if (raw.privateRepos == null) throw new JsonException("privateRepos is null");

        return new UserPlan(raw.name, raw.space, raw.collaborators, raw.privateRepos);
      }
      catch (JsonException e) {
        throw new JsonException("UserPlan parse error", e);
      }
    }

    private UserPlan(@NotNull String name, long space, long collaborators, long privateRepos) {
      this.name = name;
      this.space = space;
      this.collaborators = collaborators;
      this.privateRepos = privateRepos;
    }

    @NotNull
    public String getName() {
      return name;
    }

    public long getSpace() {
      return space;
    }

    public long getCollaborators() {
      return collaborators;
    }

    public long getPrivateRepos() {
      return privateRepos;
    }
  }

  public boolean canCreatePrivateRepo() {
    return getPlan().getPrivateRepos() > getOwnedPrivateRepos();
  }

  @NotNull
  public static GithubUserDetailed createDetailed(@Nullable GithubUserRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.type == null) throw new JsonException("type is null");
      if (raw.publicRepos == null) throw new JsonException("publicRepos is null");
      if (raw.publicGists == null) throw new JsonException("publicGists is null");
      if (raw.totalPrivateRepos == null) throw new JsonException("totalPrivateRepos is null");
      if (raw.ownedPrivateRepos == null) throw new JsonException("ownedPrivateRepos is null");
      if (raw.privateGists == null) throw new JsonException("privateGists is null");
      if (raw.diskUsage == null) throw new JsonException("diskUsage is null");

      GithubUser user = GithubUser.create(raw);
      UserPlan plan = UserPlan.create(raw.plan);

      return new GithubUserDetailed(user, raw.name, raw.email, raw.company, raw.location, raw.type, raw.publicRepos, raw.publicGists,
                                    raw.totalPrivateRepos, raw.ownedPrivateRepos, raw.privateGists, raw.diskUsage, plan);
    }
    catch (JsonException e) {
      throw new JsonException("GithubUserDetailed parse error", e);
    }
  }

  private GithubUserDetailed(@NotNull GithubUser user, @Nullable String name,
                               @Nullable String email,
                               @Nullable String company,
                               @Nullable String location,
                               @NotNull String type,
                               int publicRepos,
                               int publicGists,
                               int totalPrivateRepos,
                               int ownedPrivateRepos,
                               int privateGists,
                               long diskUsage,
                               @NotNull UserPlan plan) {
    super(user);
    this.name = name;
    this.email = email;
    this.company = company;
    this.location = location;
    this.type = type;
    this.publicRepos = publicRepos;
    this.publicGists = publicGists;
    this.totalPrivateRepos = totalPrivateRepos;
    this.ownedPrivateRepos = ownedPrivateRepos;
    this.privateGists = privateGists;
    this.diskUsage = diskUsage;
    this.plan = plan;
  }

  @Nullable
  public String getName() {
    return name;
  }

  @Nullable
  public String getEmail() {
    return email;
  }

  @Nullable
  public String getCompany() {
    return company;
  }

  @Nullable
  public String getLocation() {
    return location;
  }

  @NotNull
  public String getType() {
    return type;
  }

  public int getPublicRepos() {
    return publicRepos;
  }

  public int getPublicGists() {
    return publicGists;
  }

  public int getTotalPrivateRepos() {
    return totalPrivateRepos;
  }

  public int getOwnedPrivateRepos() {
    return ownedPrivateRepos;
  }

  public int getPrivateGists() {
    return privateGists;
  }

  public long getDiskUsage() {
    return diskUsage;
  }

  @NotNull
  public UserPlan getPlan() {
    return plan;
  }
}
