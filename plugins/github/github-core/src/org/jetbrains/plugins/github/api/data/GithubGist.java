// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"UnusedDeclaration", "MismatchedQueryAndUpdateOfCollection"})
public class GithubGist {
  private String id;
  private String description;

  @JsonProperty("public")
  private Boolean isPublic;

  private String url;
  private String htmlUrl;
  private String gitPullUrl;
  private String gitPushUrl;

  private Map<String, GistFile> files;

  private GithubUser owner;

  private Date createdAt;

  public @NotNull String getId() {
    return id;
  }

  public @NotNull String getDescription() {
    return StringUtil.notNullize(description);
  }

  public boolean isPublic() {
    return isPublic;
  }

  public @NotNull String getHtmlUrl() {
    return htmlUrl;
  }

  public @NotNull List<GistFile> getFiles() {
    return new ArrayList<>(files.values());
  }

  public @Nullable GithubUser getUser() {
    return owner;
  }

  public static class GistFile {
    private Long size;
    private String filename;
    private String content;

    private String raw_url;

    private String type;
    private String language;

    public @NotNull String getFilename() {
      return filename;
    }

    public @NotNull String getContent() {
      return content;
    }

    public @NotNull String getRawUrl() {
      return raw_url;
    }
  }
}
