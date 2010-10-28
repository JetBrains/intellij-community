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

import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.Nullable;

/**
 * Represents one part of a line annotation which is shown in the editor when the "Annotate"
 * action is invoked. Classes implementing this interface can also implement
 * {@link com.intellij.openapi.editor.EditorGutterAction} to handle clicks on the annotation.
 *
 * @author Konstantin Bulenkov
 * @see FileAnnotation#getAspects()
 */
public interface LineAnnotationAspect {
  String AUTHOR = VcsBundle.message("line.annotation.aspect.author");
  String DATE = VcsBundle.message("line.annotation.aspect.date");
  String REVISION = VcsBundle.message("line.annotation.aspect.revision");
  /**
   * Get annotation text for the specific line number
   *
   * @param line the line number to query
   * @return the annotation text
   */
  String getValue(int line);

  /**
   * Used to show a tooltip for specific line or group of lines
   *
   * @param line the line number to query
   * @return the tooltip text for the line
   */
  @Nullable
  String getTooltipText(int line);

  /**
   * Returns unique identifier, that will be used to show/hide some aspects
   * If <code>null</code> this line aspect won't be configurable in annotation settings
   *
   * @return unique id
   */
  @Nullable
  String getId();

  /**
   * Returns <code>true</code> if this aspect will be shown on Annotate action
   *
   * @return <code>true</code> if this aspect will be shown on Annotate action
   */
  boolean isShowByDefault();
}
