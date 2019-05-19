// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UnusedDeclaration")
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

  @NotNull
  public String getId() {
    return id;
  }

  @NotNull
  public String getDescription() {
    return StringUtil.notNullize(description);
  }

  public boolean isPublic() {
    return isPublic;
  }

  @NotNull
  public String getHtmlUrl() {
    return htmlUrl;
  }

  @NotNull
  public List<GistFile> getFiles() {
    return new ArrayList<>(files.values());
  }

  @Nullable
  public GithubUser getUser() {
    return owner;
  }

  public static class GistFile {
    private Long size;
    private String filename;
    private String content;

    private String raw_url;

    private String type;
    private String language;

    @NotNull
    public String getFilename() {
      return filename;
    }

    @NotNull
    public String getContent() {
      return content;
    }

    @NotNull
    public String getRawUrl() {
      return raw_url;
    }
  }
}
