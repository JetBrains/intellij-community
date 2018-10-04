// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;
import org.jetbrains.plugins.github.api.GithubFullPath;

//example/GithubRepoBasic.json
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubRepoBasic {
  private Long id;
  //private String nodeId;
  @Mandatory private String name;
  private String fullName;
  @Mandatory private GithubUser owner;
  @SerializedName("private")
  @Mandatory private Boolean isPrivate;
  @Mandatory private String htmlUrl;
  private String description;
  @SerializedName("fork")
  @Mandatory private Boolean isFork;

  private String url;
  //urls

  @NotNull
  public String getName() {
    return name;
  }

  @NotNull
  public String getDescription() {
    return StringUtil.notNullize(description);
  }

  public boolean isPrivate() {
    return isPrivate;
  }

  public boolean isFork() {
    return isFork;
  }

  @NotNull
  public String getHtmlUrl() {
    return htmlUrl;
  }

  @NotNull
  public GithubUser getOwner() {
    return owner;
  }


  @NotNull
  public String getUserName() {
    return getOwner().getLogin();
  }

  @NotNull
  public String getFullName() {
    return getUserName() + "/" + getName();
  }

  @NotNull
  public GithubFullPath getFullPath() {
    return new GithubFullPath(getUserName(), getName());
  }
}
