/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.dom.manifest;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 19, 2009
 * Time: 5:49:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidManifestUtils {
  private AndroidManifestUtils() {
  }

  @NotNull
  public static String getStyleableNameByTagName(@NotNull String tagName) {
    String prefix = "AndroidManifest";
    if (tagName.equals("manifest")) return prefix;
    String[] parts = tagName.split("-");
    StringBuilder builder = new StringBuilder(prefix);
    for (String part : parts) {
      char first = part.charAt(0);
      String remained = part.substring(1);
      builder.append(Character.toUpperCase(first)).append(remained);
    }
    return builder.toString();
  }

  @NotNull
  public static String[] getStaticallyDefinedAttrs(@NotNull ManifestElement element) {
    List<String> strings = new ArrayList<String>();
    if (element instanceof ManifestElementWithName) {
      strings.add("name");
    }
    if (element instanceof ApplicationComponent || element instanceof Application) {
      strings.add("label");
    }
    if (element instanceof Application) {
      strings.add("name");
      strings.add("manageSpaceActivity");
      strings.add("debuggable");
    }
    if (element instanceof Provider) {
      strings.add("authorities");
    }
    if (element instanceof Instrumentation) {
      strings.add("targetPackage");
    }
    return ArrayUtil.toStringArray(strings);
  }

  @NotNull
  public static String[] getStaticallyDefinedSubtags(@NotNull ManifestElement element) {
    List<String> strings = new ArrayList<String>();
    if (element instanceof Manifest) {
      Collections.addAll(strings, "application", "instrumentation", "permission", "permission-group", "permission-tree", "uses-permission");
    }
    else if (element instanceof Application) {
      Collections.addAll(strings, "activity", "service", "provider", "receiver", "uses-library");
    }
    else if (element instanceof Activity || element instanceof ActivityAlias) {
      strings.add("intent-filter");
    }
    else if (element instanceof IntentFilter) {
      strings.add("action");
      strings.add("category");
    }
    return ArrayUtil.toStringArray(strings);
  }

  @Nullable
  public static Class getClassByManifestStyleableName(@NotNull String styleableName) {
   /* String prefix = "AndroidManifest";
    if (!styleableName.startsWith(prefix)) {
      return null;
    }
    if (styleableName.equals(prefix)) {
      return Manifest.class;
    }
    String remained = styleableName.substring(prefix.length());
    try {
      return Class.forName("org.jetbrains.android.dom.manifest." + remained);
    }
    catch (ClassNotFoundException e) {
      return ManifestElement.class;
    }*/
    return ManifestElement.class;
  }

  @Nullable
  public static String getTagNameByStyleableName(@NotNull String styleableName) {
    String prefix = "AndroidManifest";
    if (!styleableName.startsWith(prefix)) {
      return null;
    }
    String remained = styleableName.substring(prefix.length());
    if (remained.length() == 0) return "manifest";
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < remained.length(); i++) {
      char c = remained.charAt(i);
      if (builder.length() > 0 && Character.isUpperCase(c)) {
        builder.append('-');
      }
      builder.append(Character.toLowerCase(c));
    }
    return builder.toString();
  }
}
