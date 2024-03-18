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
import com.intellij.openapi.util.Ref;
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
import java.util.Collection;
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

  @Override
  public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
    for (PsiElement element : elements) {
      ProgressManager.checkCanceled();
      if (!(element instanceof IgnoreEntryFile)) {
        continue;
      }

      boolean isDirectory = element instanceof IgnoreEntryDirectory;

      if (!isDirectory) {
        IgnoreEntryFile entry = (IgnoreEntryFile)element;
        VirtualFile parent = element.getContainingFile().getVirtualFile().getParent();
        Project project = element.getProject();
        VirtualFile projectDir = project.getBaseDir();
        if (parent == null || projectDir == null) {
          continue;
        }

        PatternCache patternCache = PatternCache.getInstance(element.getProject());
        Pattern pattern = patternCache.createPattern(entry);
        isDirectory = pattern != null && isDirectoryExist(parent, pattern);
      }

      if (isDirectory) {
        final PsiElement leafElement = firstLeafOrNull(element);
        if (leafElement != null) {
          result.add(new LineMarkerInfo<>(leafElement, element.getTextRange(),
                                          PlatformIcons.FOLDER_ICON, null, null, GutterIconRenderer.Alignment.CENTER));
        }
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

  @Nullable
  private static PsiElement firstLeafOrNull(@NotNull PsiElement element) {
    LeafElement firstLeaf = TreeUtil.findFirstLeaf(element.getNode());
    return firstLeaf != null ? firstLeaf.getPsi() : null;
  }

  private static boolean isDirectoryExist(@NotNull VirtualFile root, @NotNull Pattern pattern) {
    Ref<Boolean> found = Ref.create(false);
    VfsUtilCore.iterateChildrenRecursively(root, file -> file.isDirectory(), (dir) -> {
      ProgressManager.checkCanceled();
      String path = VfsUtilCore.getRelativePath(dir, root);
      if (path != null && RegexUtil.match(pattern, path)) {
        found.set(true);
        return false;
      }
      return true;
    });
    return found.get();
  }
}
