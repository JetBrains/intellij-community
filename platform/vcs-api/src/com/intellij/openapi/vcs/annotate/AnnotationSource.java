/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;

public enum AnnotationSource {
  LOCAL() {
    public ColorKey getColor() {
      return EditorColors.ANNOTATIONS_COLOR;
    }
    public boolean showMerged() {
      return false;
    }},
  MERGE() {
    public ColorKey getColor() {
      return EditorColors.ANNOTATIONS_MERGED_COLOR;
    }
    public boolean showMerged() {
      return true;
    }};

  public abstract boolean showMerged();
  public abstract ColorKey getColor();

  public static AnnotationSource getInstance(final boolean showMerged) {
    return showMerged ? MERGE : LOCAL;
  }
}
