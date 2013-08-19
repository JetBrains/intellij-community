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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public class GithubGist {
  @NotNull private final String myId;
  @NotNull private final String myDescription;

  private final boolean myIsPublic;

  @NotNull private final String myHtmlUrl;

  @NotNull private final List<GistFile> myFiles;

  @Nullable private final GithubUser myUser;

  public static class GistFile {
    @NotNull private final String myFilename;
    @NotNull private final String myContent;

    @NotNull private final String myRawUrl;

    public GistFile(@NotNull String filename, @NotNull String content, @NotNull String rawUrl) {
      myFilename = filename;
      myContent = content;
      myRawUrl = rawUrl;
    }

    @NotNull
    public String getFilename() {
      return myFilename;
    }

    @NotNull
    public String getContent() {
      return myContent;
    }

    @NotNull
    public String getRawUrl() {
      return myRawUrl;
    }
  }

  @NotNull
  public List<FileContent> getContent() {
    List<FileContent> ret = new ArrayList<FileContent>();
    for (GistFile file : getFiles()) {
      ret.add(new FileContent(file.getFilename(), file.getContent()));
    }
    return ret;
  }

  public GithubGist(@NotNull String id,
                    @Nullable String description,
                    boolean isPublic,
                    @NotNull String htmlUrl,
                    @NotNull List<GistFile> files,
                    @Nullable GithubUser user) {
    myId = id;
    myDescription = StringUtil.notNullize(description);
    myIsPublic = isPublic;
    myHtmlUrl = htmlUrl;
    myFiles = files;
    myUser = user;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  public boolean isPublic() {
    return myIsPublic;
  }

  @NotNull
  public String getHtmlUrl() {
    return myHtmlUrl;
  }

  @NotNull
  public List<GistFile> getFiles() {
    return myFiles;
  }

  @Nullable
  public GithubUser getUser() {
    return myUser;
  }

  public static class FileContent {
    @NotNull private final String myFileName;
    @NotNull private final String myContent;

    public FileContent(@NotNull String fileName, @NotNull String content) {
      myFileName = fileName;
      myContent = content;
    }

    @NotNull
    public String getFileName() {
      return myFileName;
    }

    @NotNull
    public String getContent() {
      return myContent;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FileContent that = (FileContent)o;

      if (!myContent.equals(that.myContent)) return false;
      if (!myFileName.equals(that.myFileName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myFileName.hashCode();
      result = 31 * result + myContent.hashCode();
      return result;
    }
  }
}
