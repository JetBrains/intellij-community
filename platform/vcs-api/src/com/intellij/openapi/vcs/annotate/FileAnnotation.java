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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * A provider of file annotations related to VCS
 */
public abstract class FileAnnotation {
  private final Project myProject;
  private Runnable myCloser;

  protected FileAnnotation(Project project) {
    myProject = project;
  }

  /**
   * This method is invoked when the annotation provider is no
   * more used by UI.
   */
  public abstract void dispose();

  /**
   * Get annotation aspects. The typical aspects are revision
   * number, date, author. The aspects are displayed each
   * in own column in the returned order.
   *
   * @return annotation aspects
   */
  public abstract LineAnnotationAspect[] getAspects();

  /**
   * <p>The tooltip that is shown over annotation.
   * Typically this is a comment associated with commit that has added or modified the line.</p>
   *
   * <p>If the method returns null, the tooltip is not shown for this line.</p>
   *
   * @param lineNumber the line number
   * @return the tooltip text
   */
  @Nullable
  public abstract String getToolTip(int lineNumber);

  /**
   * @return the text of the annotated file
   */
  public abstract String getAnnotatedContent();

  /**
   * Get revision number for the line.
   * when "show merge sources" is turned on, returns merge source revision
   *
   * @param lineNumber the line number
   * @return the revision number or null for lines that contain uncommitted changes.
   */
  @Nullable
  public abstract VcsRevisionNumber getLineRevisionNumber(int lineNumber);

  @Nullable
  public abstract Date getLineDate(int lineNumber);

  /**
   * Get revision number for the line.
   */
  @Nullable
  public abstract VcsRevisionNumber originalRevision(int lineNumber);

  /**
   * @return current revision of file for the moment when annotation was computed;
   * null if provider does not provide this information
   *
   * ! needed for automatic annotation close when file current revision changes
   */
  @Nullable
  public abstract VcsRevisionNumber getCurrentRevision();

  /**
   * Get all revisions that are mentioned in the annotations.
   * order: from newest to oldest
   *
   * @return the list of revisions that are mentioned in annotations. Or null
   *   if before/after popups cannot be suported by the VCS system.
   */
  @Nullable
  public abstract List<VcsFileRevision> getRevisions();

  public abstract boolean revisionsNotEmpty();

  @Nullable
  public abstract AnnotationSourceSwitcher getAnnotationSourceSwitcher();
  
  public abstract int getLineCount();

  public final void close() {
    myCloser.run();
  }

  public void setCloser(Runnable closer) {
    myCloser = closer;
  }

  public VcsKey getVcsKey() {
    return null;
  }

  public boolean isBaseRevisionChanged(final VcsRevisionNumber number) {
    final VcsRevisionNumber currentRevision = getCurrentRevision();
    return currentRevision != null && ! currentRevision.equals(number);
  }

  public abstract VirtualFile getFile();

  public void unregister() {
    ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener().unregisterAnnotation(getFile(), this);
  }
}
