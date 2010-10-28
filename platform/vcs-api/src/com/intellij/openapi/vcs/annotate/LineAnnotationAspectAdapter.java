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

import com.intellij.openapi.editor.EditorGutterAction;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class LineAnnotationAspectAdapter implements LineAnnotationAspect, EditorGutterAction {
  private final String myId;
  private final boolean myShowByDefault;

  protected LineAnnotationAspectAdapter() {
    this(null, false);
  }

  protected LineAnnotationAspectAdapter(String id) {
    this(id, false);
  }

  public LineAnnotationAspectAdapter(String id, boolean showByDefault) {
    myId = id;
    myShowByDefault = showByDefault;
  }

  public String getTooltipText(int lineNumber) {
    return null;
  }

  @Override
  public String getId() {
    return myId;
  }

  @Override
  public boolean isShowByDefault() {
    return myShowByDefault;
  }

  public Cursor getCursor(final int lineNum) {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  @Override
  public void doAction(int lineNum) {
    showAffectedPaths(lineNum);
  }

  protected abstract void showAffectedPaths(int lineNum);
}
