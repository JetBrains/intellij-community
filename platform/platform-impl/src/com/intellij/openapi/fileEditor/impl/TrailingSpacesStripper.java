/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.util.Key;

/** @deprecated use {@link com.intellij.openapi.editor.impl.TrailingSpacesStripper} (to be removed in IDEA 15) */
@SuppressWarnings("UnusedDeclaration")
public class TrailingSpacesStripper {

  public static final Key<String> OVERRIDE_STRIP_TRAILING_SPACES_KEY =
    com.intellij.openapi.editor.impl.TrailingSpacesStripper.OVERRIDE_STRIP_TRAILING_SPACES_KEY;

  public static final Key<Boolean> OVERRIDE_ENSURE_NEWLINE_KEY =
    com.intellij.openapi.editor.impl.TrailingSpacesStripper.OVERRIDE_ENSURE_NEWLINE_KEY;

}
