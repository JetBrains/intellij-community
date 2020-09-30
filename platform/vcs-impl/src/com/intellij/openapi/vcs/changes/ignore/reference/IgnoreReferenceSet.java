/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.intellij.openapi.vcs.changes.ignore.reference;

import com.intellij.lang.Language;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ignore.cache.IgnorePatternsMatchedFilesCache;
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache;
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreLanguage;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreEntry;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreFile;
import com.intellij.openapi.vcs.changes.ignore.util.RegexUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class IgnoreReferenceSet extends FileReferenceSet {

  @NotNull
  private final IgnorePatternsMatchedFilesCache myIgnorePatternsMatchedFilesCache;

  private final PatternCache myPatternCache;

  public IgnoreReferenceSet(@NotNull IgnoreEntry element) {
    super(element);
    myIgnorePatternsMatchedFilesCache = IgnorePatternsMatchedFilesCache.getInstance(element.getProject());
    myPatternCache = PatternCache.getInstance(element.getProject());
  }

  /**
   * Creates {@link IgnoreReference} instance basing on passed text value.
   *
   * @param range text range
   * @param index start index
   * @param text  string text
   * @return file reference
   */
  @Override
  public FileReference createFileReference(TextRange range, int index, String text) {
    return new IgnoreReference(this, range, index, text);
  }

  /**
   * Sets ending slash as allowed.
   *
   * @return <code>false</code>
   */
  @Override
  public boolean isEndingSlashNotAllowed() {
    return false;
  }

  /**
   * Computes current element's parent context.
   *
   * @return contexts collection
   */
  @NotNull
  @Override
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    PsiFile containingFile = getElement().getContainingFile();
    PsiDirectory containingDirectory =
      containingFile.getParent() != null ? containingFile.getParent() : containingFile.getOriginalFile().getContainingDirectory();
    if (containingDirectory == null) {
      Language language = containingFile.getLanguage();
      if (language instanceof IgnoreLanguage) {
        VirtualFile affectedRoot =
          ((IgnoreLanguage)language).getAffectedRoot(containingFile.getProject(), containingFile.getOriginalFile().getVirtualFile());
        if (affectedRoot != null) {
          containingDirectory = containingFile.getManager().findDirectory(affectedRoot);
        }
      }
    }
    return containingDirectory != null ? Collections.singletonList(containingDirectory) :
           super.computeDefaultContexts();
  }

  @Override
  protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
    return item -> {
      Project project = item.getProject();
      PsiFile originalFile = getElement().getContainingFile().getOriginalFile();
      VirtualFile ignoreFile = originalFile.getVirtualFile();
      Language language = originalFile.getLanguage();
      if (!(language instanceof IgnoreLanguage)) return false;

      VirtualFile ignoreFileAffectedRoot = ((IgnoreLanguage)language).getAffectedRoot(project, ignoreFile);
      VirtualFile ignoreFileVcsRoot = VcsUtil.getVcsRootFor(project, ignoreFileAffectedRoot);
      if (ignoreFileVcsRoot == null) return false;

      return isFileUnderSameVcsRoot(project, ignoreFileVcsRoot, item.getVirtualFile());
    };
  }

  /**
   * Returns last reference of the current element's references.
   *
   * @return last {@link FileReference}
   */
  @Override
  @Nullable
  public FileReference getLastReference() {
    FileReference lastReference = super.getLastReference();
    if (lastReference != null && lastReference.getCanonicalText().endsWith(getSeparatorString())) {
      return this.myReferences != null && this.myReferences.length > 1 ?
             this.myReferences[this.myReferences.length - 2] : null;
    }
    return lastReference;
  }

  /**
   * Disallows conversion to relative reference.
   *
   * @param relative is ignored
   * @return <code>false</code>
   */
  @Override
  public boolean couldBeConvertedTo(boolean relative) {
    return false;
  }

  /**
   * Parses entry, searches for file references and stores them in {@link #myReferences}.
   */
  @Override
  protected void reparse() {
    ProgressManager.checkCanceled();
    String str = StringUtil.trimEnd(getPathString(), getSeparatorString());
    List<FileReference> referencesList = new ArrayList<>();

    String separatorString = getSeparatorString(); // separator's length can be more then 1 char
    int sepLen = separatorString.length();
    int currentSlash = -sepLen;
    int startInElement = getStartInElement();

    // skip white space
    while (currentSlash + sepLen < str.length() && Character.isWhitespace(str.charAt(currentSlash + sepLen))) {
      currentSlash++;
    }

    if (currentSlash + sepLen + sepLen < str.length() && str.substring(currentSlash + sepLen,
                                                                       currentSlash + sepLen + sepLen).equals(separatorString)) {
      currentSlash += sepLen;
    }
    int index = 0;

    if (str.equals(separatorString)) {
      FileReference fileReference = createFileReference(
        new TextRange(startInElement, startInElement + sepLen),
        index++,
        separatorString
      );
      referencesList.add(fileReference);
    }

    while (true) {
      ProgressManager.checkCanceled();
      int nextSlash = str.indexOf(separatorString, currentSlash + sepLen);
      String subReferenceText = nextSlash > 0 ? str.substring(0, nextSlash) : str;
      TextRange range = new TextRange(startInElement + currentSlash + sepLen, startInElement +
                                                                              (nextSlash > 0 ? nextSlash : str.length()));
      FileReference ref = createFileReference(range, index++, subReferenceText);
      referencesList.add(ref);
      if ((currentSlash = nextSlash) < 0) {
        break;
      }
    }

    myReferences = referencesList.toArray(FileReference.EMPTY);
  }

  @Override
  protected String getNewAbsolutePath(@NotNull PsiFileSystemItem root, @NotNull String relativePath) {
    PsiFile ignoreFile = getContainingFile();
    VirtualFile rootVF = root.getVirtualFile();
    if (rootVF != null &&
        ignoreFile != null &&
        ignoreFile.getVirtualFile() != null &&
        ignoreFile.getVirtualFile().getParent() != null &&
        !rootVF.equals(ignoreFile.getVirtualFile().getParent())) {
      VirtualFile relativeFile = rootVF.findFileByRelativePath(relativePath);
      if (relativeFile != null) {
        String relativeToIgnoreFileParent = VfsUtilCore.getRelativePath(relativeFile, ignoreFile.getVirtualFile().getParent());
        if (relativeToIgnoreFileParent != null) {
          return absoluteUrlNeedsStartSlash() ? "/" + relativeToIgnoreFileParent : relativeToIgnoreFileParent;
        }
      }
    }
    return super.getNewAbsolutePath(root, relativePath);
  }

  /**
   * Custom definition of {@link FileReference}.
   */
  private final class IgnoreReference extends FileReference {

    private IgnoreReference(@NotNull FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
      super(fileReferenceSet, range, index, text);
    }

    /**
     * Resolves reference to the filesystem.
     *
     * @param text          entry
     * @param context       filesystem context
     * @param result        result references collection
     * @param caseSensitive is ignored
     */
    @Override
    protected void innerResolveInContext(@NotNull String text, @NotNull PsiFileSystemItem context,
                                         @NotNull Collection<ResolveResult> result, boolean caseSensitive) {
      ProgressManager.checkCanceled();
      super.innerResolveInContext(text, context, result, caseSensitive);

      PsiFile containingFile = getContainingFile();
      if (!(containingFile instanceof IgnoreFile)) {
        return;
      }
      VirtualFile ignoreFileAffectedRoot =
        ((IgnoreLanguage)containingFile.getLanguage()).getAffectedRoot(context.getProject(), containingFile.getVirtualFile());
      if (ignoreFileAffectedRoot == null) return;

      VirtualFile ignoreFileVcsRoot = VcsUtil.getVcsRootFor(context.getProject(), ignoreFileAffectedRoot);
      if (ignoreFileVcsRoot == null) return;

      VirtualFile contextVirtualFile = context.getVirtualFile();

      if (contextVirtualFile != null) {
        IgnoreEntry entry = (IgnoreEntry)getFileReferenceSet().getElement();
        String current = getCanonicalText();
        Pattern pattern = myPatternCache.createPattern(current, entry.getSyntax());
        if (pattern != null) {
          PsiDirectory parent = getElement().getContainingFile().getParent();
          VirtualFile root = parent != null ? parent.getVirtualFile() : null;
          PsiManager psiManager = getElement().getManager();

          List<VirtualFile> files = new ArrayList<>(myIgnorePatternsMatchedFilesCache.getFilesForPattern(pattern));
          if (files.isEmpty()) {
            files.addAll(ContainerUtil.filter(
              context.getVirtualFile().getChildren(),
              virtualFile -> isFileUnderSameVcsRoot(context.getProject(), ignoreFileVcsRoot, virtualFile)
            ));
          }

          for (VirtualFile file : files) {
            ProgressManager.checkCanceled();
            if (!isFileUnderSameVcsRoot(context.getProject(), ignoreFileVcsRoot, file)) {
              continue;
            }

            String relativeToIgnoreFileVcsRoot = VfsUtilCore.getRelativePath(file, ignoreFileVcsRoot);
            String name = root != null
                          ? VfsUtilCore.getRelativePath(file, root)
                          : relativeToIgnoreFileVcsRoot != null ? relativeToIgnoreFileVcsRoot : file.getName();
            if (RegexUtil.match(pattern, name)) {
              PsiFileSystemItem psiFileSystemItem = getPsiFileSystemItem(psiManager, file);
              if (psiFileSystemItem == null) {
                continue;
              }
              result.add(new PsiElementResolveResult(psiFileSystemItem));
            }
          }
        }
      }
    }

    /**
     * Searches for directory or file using {@link PsiManager}.
     *
     * @param manager {@link PsiManager} instance
     * @param file    working file
     * @return Psi item
     */
    @Nullable
    private PsiFileSystemItem getPsiFileSystemItem(@NotNull PsiManager manager, @NotNull VirtualFile file) {
      if (!file.isValid()) {
        return null;
      }
      return file.isDirectory() ? manager.findDirectory(file) : manager.findFile(file);
    }
  }

  private static boolean isFileUnderSameVcsRoot(@NotNull Project project, @NotNull VirtualFile vcsRoot, @NotNull VirtualFile file) {
    VirtualFile fileVcsRoot = VcsUtil.getVcsRootFor(project, file);
    return fileVcsRoot != null && vcsRoot.equals(fileVcsRoot);
  }
}
