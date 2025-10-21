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

package com.intellij.openapi.vcs.changes.ignore.codeInsight;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreEntryDirectory;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreEntryFile;
import com.intellij.openapi.vcs.changes.ignore.util.RegexUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@link LineMarkerProvider} that marks ignore entry lines with {@link PlatformIcons#FOLDER_ICON} if they point to the directory in file
 * system.
 */
public final class IgnoreDirectoryMarkerProvider extends LineMarkerProviderDescriptor implements DumbAware {

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  private record IgnoreFileRecord(@NotNull IgnoreEntryFile entry, @NotNull Pattern pattern) {}
  @Override
  public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
    if (elements.isEmpty()) return;
    Collection<IgnoreFileRecord> ignoreEntryFiles = new HashSet<>(elements.size());
    List<IgnoreEntryFile> ignoreEntryDirectories = new ArrayList<>(elements.size());
    Project project = elements.get(0).getProject();
    PatternCache patternCache = PatternCache.getInstance(project);
    VirtualFile parent = elements.get(0).getContainingFile().getVirtualFile().getParent();
    VirtualFile projectDir = project.getBaseDir();
    boolean isRootFile = parent == null || projectDir == null;
    for (PsiElement element : elements) {
      ProgressManager.checkCanceled();
      if (element instanceof IgnoreEntryFile entry) {
        if (entry instanceof IgnoreEntryDirectory dir) {
          ignoreEntryDirectories.add(dir);
        }
        else {
          Pattern pattern = patternCache.createPattern(entry);
          if (pattern != null && !isRootFile) {
            ignoreEntryFiles.add(new IgnoreFileRecord(entry, pattern));
          }
        }
      }
    }

    if (!ignoreEntryFiles.isEmpty()) {
      computerDirectoriesExist(parent, ignoreEntryFiles, ignoreEntryDirectories);
    }

    for (IgnoreEntryFile directory : ignoreEntryDirectories) {
      ProgressManager.checkCanceled();
      final PsiElement leafElement = firstLeafOrNull(directory);
      if (leafElement != null) {
        result.add(new LineMarkerInfo<>(leafElement, directory.getTextRange(),
                                        PlatformIcons.FOLDER_ICON, null, null, GutterIconRenderer.Alignment.CENTER));
      }
    }
  }

  @Override
  public @NotNull String getName() {
    return VcsBundle.message("gutter.name.version.control.ignored.directories");
  }

  @Override
  public @NotNull Icon getIcon() {
    return PlatformIcons.FOLDER_ICON;
  }

  private static @Nullable PsiElement firstLeafOrNull(@NotNull PsiElement element) {
    LeafElement firstLeaf = TreeUtil.findFirstLeaf(element.getNode());
    return firstLeaf != null ? firstLeaf.getPsi() : null;
  }

  private static void computerDirectoriesExist(@NotNull VirtualFile root, @NotNull Collection<IgnoreFileRecord> fileRecords,
                                               @NotNull List<? super IgnoreEntryFile> ignoreEntryDirectories) {
    VfsUtilCore.iterateChildrenRecursively(root, file -> file.isDirectory(), dir -> {
      ProgressManager.checkCanceled();
      String path = VfsUtilCore.getRelativePath(dir, root);
      if (path != null) {
        for (IgnoreFileRecord fileRecord : fileRecords) {
          if (RegexUtil.match(fileRecord.pattern, path)) {
            // directory matched, add it to the directory list
            fileRecords.remove(fileRecord);
            ignoreEntryDirectories.add(fileRecord.entry());
            if (fileRecords.isEmpty()) {
              return false;
            }
            break;
          }
        }
      }
      return true;
    });
  }
}
