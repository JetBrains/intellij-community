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
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.*;

import static com.intellij.openapi.vcs.VcsBundle.BUNDLE;

@ApiStatus.Internal
public enum ColorMode {
  AUTHOR("author", "annotations.color.mode.author"),
  ORDER("order", "annotations.color.mode.order"),
  NONE("none", "annotations.color.mode.hide");

  private static final String KEY = "annotate.color.mode"; //NON-NLS
  private final String myId;
  private final String myDescriptionKey;

  ColorMode(@NotNull @NonNls String id,
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
}
