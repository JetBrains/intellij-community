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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents annotations ("vcs blame") for some file in a specific revision
 * @see AnnotationProvider
 */
public abstract class FileAnnotation {
  private static final Logger LOG = Logger.getInstance(FileAnnotation.class);

  @NotNull private final Project myProject;

  private boolean myIsClosed;
  private Runnable myCloser;
  private Consumer<FileAnnotation> myReloader;

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


  public synchronized boolean isClosed() {
    return myIsClosed;
  }

  /**
   * Notify that annotations should be closed
   */
  public synchronized final void close() {
    myIsClosed = true;
    if (myCloser != null) {
      myCloser.run();

      myCloser = null;
      myReloader = null;
    }
  }

  /**
   * Notify that annotation information has changed, and should be updated.
   * If `this` is visible, hide it and show new one instead.
   * If `this` is not visible, do nothing.
   *
   * @param newFileAnnotation annotations to be shown
   */
  public synchronized final void reload(@NotNull FileAnnotation newFileAnnotation) {
    if (myReloader != null) myReloader.consume(newFileAnnotation);
  }

  /**
   * @see #close()
   */
  public synchronized final void setCloser(@NotNull Runnable closer) {
    if (myIsClosed) return;
    myCloser = closer;
  }

  /**
   * @see #reload()
   */
  public synchronized final void setReloader(@Nullable Consumer<FileAnnotation> reloader) {
    if (myIsClosed) return;
    myReloader = reloader;
  }


  @Deprecated
  public boolean revisionsNotEmpty() {
    return true;
  }


  @Nullable
  public CurrentFileRevisionProvider getCurrentFileRevisionProvider() {
    return createDefaultCurrentFileRevisionProvider(this);
  }

  @Nullable
  public PreviousFileRevisionProvider getPreviousFileRevisionProvider() {
    return createDefaultPreviousFileRevisionProvider(this);
  }

  @Nullable
  public AuthorsMappingProvider getAuthorsMappingProvider() {
    return createDefaultAuthorsMappingProvider(this);
  }

  @Nullable
  public RevisionsOrderProvider getRevisionsOrderProvider() {
    return createDefaultRevisionsOrderProvider(this);
  }


  public interface CurrentFileRevisionProvider {
    @Nullable
    VcsFileRevision getRevision(int lineNumber);
  }

  public interface PreviousFileRevisionProvider {
    @Nullable
    VcsFileRevision getPreviousRevision(int lineNumber);

    @Nullable
    VcsFileRevision getLastRevision();
  }

  public interface AuthorsMappingProvider {
    @NotNull
    Map<VcsRevisionNumber, String> getAuthors();
  }

  public interface RevisionsOrderProvider {
    @NotNull
    List<List<VcsRevisionNumber>> getOrderedRevisions();
  }


  @Nullable
  private static CurrentFileRevisionProvider createDefaultCurrentFileRevisionProvider(@NotNull FileAnnotation annotation) {
    List<VcsFileRevision> revisions = annotation.getRevisions();
    if (revisions == null) return null;


    Map<VcsRevisionNumber, VcsFileRevision> map = new HashMap<>();
    for (VcsFileRevision revision : revisions) {
      map.put(revision.getRevisionNumber(), revision);
    }

    List<VcsFileRevision> lineToRevision = new ArrayList<>(annotation.getLineCount());
    for (int i = 0; i < annotation.getLineCount(); i++) {
      lineToRevision.add(map.get(annotation.getLineRevisionNumber(i)));
    }

    return (lineNumber) -> {
      LOG.assertTrue(lineNumber >= 0 && lineNumber < lineToRevision.size());
      return lineToRevision.get(lineNumber);
    };
  }

  @Nullable
  private static PreviousFileRevisionProvider createDefaultPreviousFileRevisionProvider(@NotNull FileAnnotation annotation) {
    List<VcsFileRevision> revisions = annotation.getRevisions();
    if (revisions == null) return null;

    Map<VcsRevisionNumber, VcsFileRevision> map = new HashMap<>();
    for (int i = 0; i < revisions.size(); i++) {
      VcsFileRevision revision = revisions.get(i);
      VcsFileRevision previousRevision = i + 1 < revisions.size() ? revisions.get(i + 1) : null;
      map.put(revision.getRevisionNumber(), previousRevision);
    }

    List<VcsFileRevision> lineToRevision = new ArrayList<>(annotation.getLineCount());
    for (int i = 0; i < annotation.getLineCount(); i++) {
      lineToRevision.add(map.get(annotation.getLineRevisionNumber(i)));
    }

    VcsFileRevision lastRevision = ContainerUtil.getFirstItem(revisions);

    return new PreviousFileRevisionProvider() {
      @Nullable
      @Override
      public VcsFileRevision getPreviousRevision(int lineNumber) {
        LOG.assertTrue(lineNumber >= 0 && lineNumber < lineToRevision.size());
        return lineToRevision.get(lineNumber);
      }

      @Nullable
      @Override
      public VcsFileRevision getLastRevision() {
        return lastRevision;
      }
    };
  }

  @Nullable
  private static AuthorsMappingProvider createDefaultAuthorsMappingProvider(@NotNull FileAnnotation annotation) {
    List<VcsFileRevision> revisions = annotation.getRevisions();
    if (revisions == null) return null;

    Map<VcsRevisionNumber, String> authorsMapping = new HashMap<>();
    for (VcsFileRevision revision : revisions) {
      String author = revision.getAuthor();
      if (author != null) authorsMapping.put(revision.getRevisionNumber(), author);
    }

    return () -> authorsMapping;
  }

  @Nullable
  private static RevisionsOrderProvider createDefaultRevisionsOrderProvider(@NotNull FileAnnotation annotation) {
    List<VcsFileRevision> revisions = annotation.getRevisions();
    if (revisions == null) return null;

    List<List<VcsRevisionNumber>> orderedRevisions = ContainerUtil.map(revisions, (revision) -> {
      return Collections.singletonList(revision.getRevisionNumber());
    });

    return () -> orderedRevisions;
  }
}
