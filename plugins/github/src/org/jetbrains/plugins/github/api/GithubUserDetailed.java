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
  @Nullable private String myName;
  @Nullable private String myEmail;
  @Nullable private String myCompany;
  @Nullable private String myLocation;

  private int myPublicRepos;
  private int myPublicGists;
  private int myTotalPrivateRepos;
  private int myOwnedPrivateRepos;
  private int myPrivateGists;
  private long myDiskUsage;

  @NotNull private String myType;
  @NotNull private UserPlan myPlan;

  public static class UserPlan implements Serializable {
    @NotNull private String myName;
    private long mySpace;
    private long myCollaborators;
    private long myPrivateRepos;

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
      this.myName = name;
      this.mySpace = space;
      this.myCollaborators = collaborators;
      this.myPrivateRepos = privateRepos;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    public long getSpace() {
      return mySpace;
    }

    public long getCollaborators() {
      return myCollaborators;
    }

    public long getPrivateRepos() {
      return myPrivateRepos;
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
    this.myName = name;
    this.myEmail = email;
    this.myCompany = company;
    this.myLocation = location;
    this.myType = type;
    this.myPublicRepos = publicRepos;
    this.myPublicGists = publicGists;
    this.myTotalPrivateRepos = totalPrivateRepos;
    this.myOwnedPrivateRepos = ownedPrivateRepos;
    this.myPrivateGists = privateGists;
    this.myDiskUsage = diskUsage;
    this.myPlan = plan;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Nullable
  public String getEmail() {
    return myEmail;
  }

  @Nullable
  public String getCompany() {
    return myCompany;
  }

  @Nullable
  public String getLocation() {
    return myLocation;
  }

  @NotNull
  public String getType() {
    return myType;
  }

  public int getPublicRepos() {
    return myPublicRepos;
  }

  public int getPublicGists() {
    return myPublicGists;
  }

  public int getTotalPrivateRepos() {
    return myTotalPrivateRepos;
  }

  public int getOwnedPrivateRepos() {
    return myOwnedPrivateRepos;
  }

  public int getPrivateGists() {
    return myPrivateGists;
  }

  public long getDiskUsage() {
    return myDiskUsage;
  }

  @NotNull
  public UserPlan getPlan() {
    return myPlan;
  }
}
