// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration", "MismatchedQueryAndUpdateOfCollection"})
public class GithubGistRequest {
  private final @NotNull String description;
  private final @NotNull Map<String, GistFile> files;

  @JsonProperty("public")
  private final boolean isPublic;

  public static class GistFile {
    private final @NotNull String content;

    public GistFile(@NotNull String content) {
      this.content = content;
    }
  }

  public GithubGistRequest(@NotNull List<FileContent> files, @NotNull String description, boolean isPublic) {
    this.description = description;
    this.isPublic = isPublic;

    this.files = new HashMap<>();
    for (FileContent file : files) {
      this.files.put(file.getFileName(), new GistFile(file.getContent()));
    }
  }

  public static class FileContent {
    private final @NotNull String myFileName;
    private final @NotNull String myContent;

    public FileContent(@NotNull String fileName, @NotNull String content) {
      myFileName = fileName;
      myContent = content;
    }

    public @NotNull String getFileName() {
      return myFileName;
    }

    public @NotNull String getContent() {
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
