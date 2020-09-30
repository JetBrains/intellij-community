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

package git4idea.ignore.lang;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType;
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreLanguage;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Git exclude (.git/info/exclude) {@link IgnoreLanguage} definition.
 */
public final class GitExcludeLanguage extends IgnoreLanguage {

  public static final GitExcludeLanguage INSTANCE = new GitExcludeLanguage();

  private GitExcludeLanguage() {
    super("GitExclude", "exclude");
  }

  @NotNull
  @Override
  public String getFilename() {
    return super.getExtension();
  }

  /**
   * Language file type.
   *
   * @return {@link GitExcludeFileType} instance
   */
  @NotNull
  @Override
  public IgnoreFileType getFileType() {
    return GitExcludeFileType.INSTANCE;
  }

  @Nullable
  @Override
  public VirtualFile getAffectedRoot(@NotNull Project project, @NotNull VirtualFile ignoreFile) {
    //ignoreFile = .git/info/exclude
    GitRepository repository = findRepository(project, ignoreFile);
    if (repository == null) return null;

    return repository.getRoot();
  }

  @Nullable
  private static GitRepository findRepository(@NotNull Project project, @NotNull VirtualFile excludeFile) {
    String excludeFilePath = excludeFile.getPath();
    for (GitRepository repository : GitRepositoryManager.getInstance(project).getRepositories()) {
      if (repository.getRepositoryFiles().isExclude(excludeFilePath)) {
        return repository;
      }
    }
    return null;
  }
}
