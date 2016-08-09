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

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.impl.gson.Mandatory;
import com.intellij.tasks.impl.gson.RestModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubGist {
  @Mandatory private String id;
  private String description;

  @SerializedName("public")
  @Mandatory private Boolean isPublic;

  private String url;
  @Mandatory private String htmlUrl;
  private String gitPullUrl;
  private String gitPushUrl;

  @Mandatory private Map<String, GistFile> files;

  private GithubUser owner;

  private Date createdAt;

  @RestModel
  public static class GistFile {
    private Long size;
    @Mandatory private String filename;
    @Mandatory private String content;

    @Mandatory private String raw_url;

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
}
