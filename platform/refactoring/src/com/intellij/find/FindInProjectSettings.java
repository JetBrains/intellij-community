// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface FindInProjectSettings {

  static FindInProjectSettings getInstance(Project project) {
    return project.getService(FindInProjectSettings.class);
  }

  void addStringToFind(@NotNull String s);

  void addStringToReplace(@NotNull String s);

  void addDirectory(@NotNull String s);

  String @NotNull [] getRecentFindStrings();

  @NlsSafe String getMostRecentFindString();

  String @NotNull [] getRecentReplaceStrings();

  @NlsSafe String getMostRecentReplaceString();

  @NotNull
  List<@NlsSafe String> getRecentDirectories();
}