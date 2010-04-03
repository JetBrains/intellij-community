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

import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A provider of file annotations related to VCS
 */
public interface FileAnnotation {
  /**
   * Add the listener that is notified when annotations might
   * have been changed.
   *
   * @param listener the listener to add
   * @see #removeListener(AnnotationListener)
   */
  void addListener(AnnotationListener listener);

  /**
   * Remove the listener
   * @param listener the listener to remove
   * @see #addListener(AnnotationListener)
   */
  void removeListener(AnnotationListener listener);

  /**
   * This method is invoked when the annotation provider is no
   * more used by UI.
   */
  void dispose();

  /**
   * Get annotation aspects. The typical aspects are revision
   * number, date, author. The aspects are displayed each
   * in own column in the returned order.
   *
   * @return annotation aspects
   */
  LineAnnotationAspect[] getAspects();

  /**
   * The tooltip that is shown over annotation. Typically this
   * is a comment associated with commit that has added or modified
   * the line.
   *
   * @param lineNumber the line number
   * @return the tooltip text
   */
  String getToolTip(int lineNumber);

  /**
   * @return the text of the annotated file
   */
  String getAnnotatedContent();

  /**
   * Get revision number for the line.
   * when "show merge sources" is turned on, returns merge source revision
   *
   * @param lineNumber the line number
   * @return the revision number or null for lines that contain uncommitted changes.
   */
  @Nullable
  VcsRevisionNumber getLineRevisionNumber(int lineNumber);

  /**
   * Get revision number for the line.
   */
  @Nullable
  VcsRevisionNumber originalRevision(int lineNumber);

  /**
   * Get all revisions that are mentioned in the annotations
   *
   * @return the list of revisions that are mentioned in annotations. Or null
   *   if before/after popups cannot be suported by the VCS system.
   */
  @Nullable
  List<VcsFileRevision> getRevisions();

  boolean revisionsNotEmpty();

  @Nullable
  AnnotationSourceSwitcher getAnnotationSourceSwitcher();
}
