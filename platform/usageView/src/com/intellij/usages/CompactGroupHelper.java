// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CompactGroupHelper {

  public static @NotNull List<String> findLongestCommonParent(@NotNull String path1, @NotNull String path2) {
    ArrayList<String> result = new ArrayList<>();
    List<String> path1Arr = pathToPathList(path1);
    List<String> path2Arr = pathToPathList(path2);
    if (path1Arr.size() > path2Arr.size()) {
      path1Arr = pathToPathList(path2);
      path2Arr = pathToPathList(path1);
    }

    ArrayList<String> arrayList = new ArrayList<>();

    for (int i = 0; i < path1Arr.size(); i++) {
      if (path1Arr.get(i).equals(path2Arr.get(i))) {
        arrayList.add(path1Arr.get(i));
      }
      else {
        break;
      }
    }
    if (arrayList.isEmpty()) return arrayList;

    String commonPath = pathListToPath(arrayList);
    result.add(commonPath);

    String rel1 = getRelativePath(path1, commonPath);
    String rel2 = getRelativePath(path2, commonPath);
    if (!rel1.isEmpty()) {
      result.add(rel1);
    }
    if (!rel2.isEmpty()) {
      result.add(rel2);
    }

    return result;
  }

  public static @NotNull List<String> pathToPathList(@NotNull String parentTextOrig) {
    if (parentTextOrig.contains("!")) {
      int index = parentTextOrig.indexOf("!");
      String zip = parentTextOrig.substring(1, index + 1);
      String rest = parentTextOrig.substring(index + 1);
      ArrayList<String> subPaths = new ArrayList<>();
      subPaths.add(zip);
      subPaths.addAll(Arrays.stream(rest.split("/")).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
      return subPaths;
    }
    return Arrays.stream(parentTextOrig.split("/")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

  protected static @NotNull String pathListToPath(@NotNull List<String> textArr) {
    Optional<String> path = textArr.stream().reduce((s1, s2) -> s1 + "/" + s2);
    return path.map(s -> "/" + s).orElse("");
  }

  protected static @NotNull String getRelativePath(@NotNull String fullPath, @NotNull String path) {
    if (path.length() >= fullPath.length()) {
      return "";
    }
    return fullPath.substring(path.length());
  }

  public static boolean listStartsWith(@NotNull List<String> path, @NotNull List<String> path1) {
    if (path1.size() > path.size()) {
      return false;
    }
    for (int i = 0; i < path1.size(); i++) {
      if (!path.get(i).equals(path1.get(i))) {
        return false;
      }
    }
    return true;
  }
}
