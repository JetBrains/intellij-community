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
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;

import static com.intellij.openapi.vcs.VcsBundle.BUNDLE;

/**
 * @author Konstantin Bulenkov
 */
public enum ShortNameType {
  INITIALS("initials", "annotations.short.name.type.initials"),
  LASTNAME("lastname", "annotations.short.name.type.last.name"),
  FIRSTNAME("firstname", "annotations.short.name.type.first.name"),
  NONE("full", "annotations.short.name.type.full.name");

  private static final String KEY = "annotate.short.names.type"; // NON-NLS
  private final String myId;
  private final String myDescriptionKey;

  ShortNameType(@NotNull @NonNls String id,
                @NotNull @PropertyKey(resourceBundle = BUNDLE) String descriptionKey) {
    myId = id;
    myDescriptionKey = descriptionKey;
  }

  @NlsActions.ActionText
  public String getDescription() {
    return VcsBundle.message(myDescriptionKey);
  }

  boolean isSet() {
    return myId.equals(PropertiesComponent.getInstance().getValue(KEY));
  }

  void set() {
    PropertiesComponent.getInstance().setValue(KEY, myId);
  }

  @Nullable
  public static String shorten(@Nullable String name, @NotNull ShortNameType type) {
    if (name == null) return null;
    if (type == NONE) return name;

    // Vasya Pupkin <vasya.pupkin@jetbrains.com> -> Vasya Pupkin
    final int[] ind = {name.indexOf('<'), name.indexOf('@'), name.indexOf('>')};
    if (0 < ind[0] && ind[0] < ind[1] && ind[1] < ind[2]) {
      name = name.substring(0, ind[0]).trim();
    }

    // vasya.pupkin@email.com --> vasya pupkin
    if (!name.contains(" ") && name.contains("@")) { //simple e-mail check. john@localhost
      name = name.substring(0, name.indexOf('@'));
    }
    name = name.replace('.', ' ').replace('_', ' ').replace('-', ' ');

    final List<String> strings = StringUtil.split(name, " ");

    if (type == INITIALS) {
      return StringUtil.join(strings, it -> String.valueOf(StringUtil.toUpperCase(it.charAt(0))), "");
    }

    if (strings.size() < 2) return name;

    String shortName;
    if (type == FIRSTNAME) {
      shortName = strings.get(0);
    }
    else {
      shortName = strings.get(strings.size() - 1);
    }
    return StringUtil.capitalize(shortName);
  }
}
