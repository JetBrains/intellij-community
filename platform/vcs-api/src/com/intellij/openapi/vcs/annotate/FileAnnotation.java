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

import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * Represents annotations ("vcs blame") for some file in a specific revision
 * @see AnnotationProvider
 */
public abstract class FileAnnotation {
  @NotNull private final Project myProject;

  private Runnable myCloser;

  protected FileAnnotation(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  public VcsKey getVcsKey() {
    return null;
  }

  /**
   * @return annotated file
   * <p>
   * If annotations are called on a local file, it can be this file.
   * If annotations are called on a specific revision, it can be corresponding {@link VcsVirtualFile}.
   * Note: file content might differ from content in annotated revision {@link #getAnnotatedContent}.
   */
  @Nullable
  public VirtualFile getFile() {
    return null;
  }

  /**
   * @return file content in the annotated revision
   * <p>
   * It might differ from {@code getFile()} content. Ex: annotations for a local file, that has non-committed changes.
   * In this case {@link UpToDateLineNumberProvider} will be used to transfer lines between local and annotated revisions.
   */
  @Nullable
  public abstract String getAnnotatedContent();


  /**
   * @return annotated revision
   * <p>
   * This information might be used to close annotations on local file if current revision was changed,
   * and invocation of AnnotationProvider on this file will produce different results - see {@link #isBaseRevisionChanged}.
   */
  @Nullable
  public abstract VcsRevisionNumber getCurrentRevision();

  /**
   * @param number current revision number {@link DiffProvider#getCurrentRevision}
   * @return whether annotations should be updated
   */
  public boolean isBaseRevisionChanged(@NotNull VcsRevisionNumber number) {
    final VcsRevisionNumber currentRevision = getCurrentRevision();
    return currentRevision != null && !currentRevision.equals(number);
  }


  /**
   * This method is invoked when the file annotation is no longer used.
   * NB: method might be invoked multiple times
   */
  public abstract void dispose();

  /**
   * Get annotation aspects.
   * The typical aspects are revision number, date, author.
   * The aspects are displayed each in own column in the returned order.
   */
  @NotNull
  public abstract LineAnnotationAspect[] getAspects();


  /**
   * @return number of lines in annotated content
   */
  public abstract int getLineCount();

  /**
   * The tooltip that is shown over annotation.
   * Typically, this is a detailed info about related revision. ex: long revision number, commit message
   */
  @Nullable
  public abstract String getToolTip(int lineNumber);

  /**
   * @return last revision that modified this line.
   */
  @Nullable
  public abstract VcsRevisionNumber getLineRevisionNumber(int lineNumber);

  /**
   * @return time of the last modification of this line.
   * Typically, this is a timestamp associated with {@link #getLineRevisionNumber}
   */
  @Nullable
  public abstract Date getLineDate(int lineNumber);


  /**
   * @return revisions that are mentioned in the annotations, from newest to oldest
   * Can be used to sort revisions, if they can't be sorted by {@code Date} or show file modification number for a revision.
   */
  @Nullable
  public abstract List<VcsFileRevision> getRevisions();


  /**
   * Allows to switch between different representation modes.
   * <p>
   * Ex: in SVN it's possible to show revision that modified line - "svn blame -g",
   * or the commit that merged that change into current branch - "svn blame".
   * <p>
   * when "show merge sources" is turned on, {@link #getLineRevisionNumber} returns merge source revision,
   * while {@link #originalRevision} returns merge revision.
   */
  @Nullable
  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    return null;
  }

  /**
   * @return last revision that modified this line in current branch.
   * @see #getAnnotationSourceSwitcher()
   * @see #getLineRevisionNumber(int)
   */
  @Nullable
  public VcsRevisionNumber originalRevision(int lineNumber) {
    return getLineRevisionNumber(lineNumber);
  }


  /**
   * Notify that annotations should be closed
   */
  public final void close() {
    myCloser.run();
  }

  /**
   * @see #close()
   */
  public final void setCloser(@NotNull Runnable closer) {
    myCloser = closer;
  }


  @Deprecated
  public boolean revisionsNotEmpty() {
    return true;
  }
}
