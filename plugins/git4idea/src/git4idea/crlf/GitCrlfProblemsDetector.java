// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.crlf;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.attributes.GitAttribute;
import git4idea.attributes.GitCheckAttrParser;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.config.GitConfigUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Given a number of files, detects if CRLF line separators in them are about to be committed to Git. That is:
 * <ul>
 *   <li>Checks if {@code core.autocrlf} is set to {@code true} or {@code input}.</li>
 *   <li>If not, checks if files contain CRLFs.</li>
 *   <li>
 *     For files with CRLFs checks if there are gitattributes set on them, such that would either force CRLF conversion on checkin,
 *     either indicate that these CRLFs are here intentionally.
 *   </li>
 * </ul>
 * All checks are made only for Windows system.
 *
 * Everywhere in the detection process we fail gracefully in case of exceptions: we log the fact, but don't fail totally, preferring to
 * tell that the calling code should not warn the user.
 *
 * @author Kirill Likhodedov
 */
public final class GitCrlfProblemsDetector {

  private static final Logger LOG = Logger.getInstance(GitCrlfProblemsDetector.class);
  private static final String CRLF = "\r\n";

  @NotNull private final Git myGit;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  private final boolean myShouldWarn;

  @NotNull
  public static GitCrlfProblemsDetector detect(@NotNull Project project, @NotNull Git git, @NotNull Collection<VirtualFile> files) {
    return new GitCrlfProblemsDetector(project, git, files);
  }

  private GitCrlfProblemsDetector(@NotNull Project project, @NotNull Git git,
                                  @NotNull Collection<VirtualFile> files) {
    myRepositoryManager = GitUtil.getRepositoryManager(project);
    myGit = git;

    Map<VirtualFile, List<VirtualFile>> filesByRoots = GitUtil.sortFilesByGitRootIgnoringMissing(project, files);

    boolean shouldWarn = false;
    Collection<VirtualFile> rootsWithIncorrectAutoCrlf = getRootsWithIncorrectAutoCrlf(filesByRoots);
    if (!rootsWithIncorrectAutoCrlf.isEmpty()) {
      Map<VirtualFile, Collection<VirtualFile>> crlfFilesByRoots = findFilesWithCrlf(filesByRoots, rootsWithIncorrectAutoCrlf);
      if (!crlfFilesByRoots.isEmpty()) {
        Map<VirtualFile, Collection<VirtualFile>> crlfFilesWithoutAttrsByRoots = findFilesWithoutAttrs(crlfFilesByRoots);
        shouldWarn = !crlfFilesWithoutAttrsByRoots.isEmpty();
      }
    }
    myShouldWarn = shouldWarn;
  }

  private Map<VirtualFile, Collection<VirtualFile>> findFilesWithoutAttrs(Map<VirtualFile, Collection<VirtualFile>> filesByRoots) {
    Map<VirtualFile, Collection<VirtualFile>> filesWithoutAttrsByRoot = new HashMap<>();
    for (Map.Entry<VirtualFile, Collection<VirtualFile>> entry : filesByRoots.entrySet()) {
      VirtualFile root = entry.getKey();
      Collection<VirtualFile> files = entry.getValue();
      Collection<VirtualFile> filesWithoutAttrs = findFilesWithoutAttrs(root, files);
      if (!filesWithoutAttrs.isEmpty()) {
        filesWithoutAttrsByRoot.put(root, filesWithoutAttrs);
      }
    }
    return filesWithoutAttrsByRoot;
  }

  @NotNull
  private Collection<VirtualFile> findFilesWithoutAttrs(@NotNull VirtualFile root, @NotNull Collection<? extends VirtualFile> files) {
    GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      LOG.warn("Repository is null for " + root);
      return Collections.emptyList();
    }
    Collection<String> interestingAttributes = Arrays.asList(GitAttribute.TEXT.getName(), GitAttribute.CRLF.getName());
    GitCommandResult result = myGit.checkAttr(repository, interestingAttributes, files);
    if (!result.success()) {
      LOG.warn(String.format("Couldn't git check-attr. Attributes: %s, files: %s", interestingAttributes, files));
      return Collections.emptyList();
    }
    GitCheckAttrParser parser = GitCheckAttrParser.parse(result.getOutput());
    Map<String, Collection<GitAttribute>> attributes = parser.getAttributes();
    Collection<VirtualFile> filesWithoutAttrs = new ArrayList<>();
    for (VirtualFile file : files) {
      ProgressIndicatorProvider.checkCanceled();
      String relativePath = FileUtil.getRelativePath(root.getPath(), file.getPath(), '/');
      Collection<GitAttribute> attrs = attributes.get(relativePath);
      if (attrs == null || !attrs.contains(GitAttribute.TEXT) && !attrs.contains(GitAttribute.CRLF)) {
        filesWithoutAttrs.add(file);
      }
    }
    return filesWithoutAttrs;
  }

  @NotNull
  private static Map<VirtualFile, Collection<VirtualFile>> findFilesWithCrlf(@NotNull Map<VirtualFile, List<VirtualFile>> allFilesByRoots,
                                                                             @NotNull Collection<VirtualFile> rootsWithIncorrectAutoCrlf) {
    Map<VirtualFile, Collection<VirtualFile>> filesWithCrlfByRoots = new HashMap<>();
    for (Map.Entry<VirtualFile, List<VirtualFile>> entry : allFilesByRoots.entrySet()) {
      VirtualFile root = entry.getKey();
      List<VirtualFile> files = entry.getValue();
      if (rootsWithIncorrectAutoCrlf.contains(root)) {
        Collection<VirtualFile> filesWithCrlf = findFilesWithCrlf(files);
        if (!filesWithCrlf.isEmpty()) {
          filesWithCrlfByRoots.put(root, filesWithCrlf);
        }
      }
    }
    return filesWithCrlfByRoots;
  }

  @NotNull
  private static Collection<VirtualFile> findFilesWithCrlf(@NotNull Collection<? extends VirtualFile> files) {
    Collection<VirtualFile> filesWithCrlf = new ArrayList<>();
    for (VirtualFile file : files) {
      ProgressIndicatorProvider.checkCanceled();
      String separator = LoadTextUtil.detectLineSeparator(file, true);
      if (CRLF.equals(separator)) {
        filesWithCrlf.add(file);
      }
    }
    return filesWithCrlf;
  }

  @NotNull
  private Collection<VirtualFile> getRootsWithIncorrectAutoCrlf(@NotNull Map<VirtualFile, List<VirtualFile>> filesByRoots) {
    Collection<VirtualFile> rootsWithIncorrectAutoCrlf = new ArrayList<>();
    for (Map.Entry<VirtualFile, List<VirtualFile>> entry : filesByRoots.entrySet()) {
      VirtualFile root = entry.getKey();
      boolean autocrlf = isAutoCrlfSetRight(root);
      if (!autocrlf) {
        rootsWithIncorrectAutoCrlf.add(root);
      }
    }
    return rootsWithIncorrectAutoCrlf;
  }

  private boolean isAutoCrlfSetRight(@NotNull VirtualFile root) {
    GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      LOG.warn("Repository is null for " + root);
      return true;
    }
    GitCommandResult result = myGit.config(repository, GitConfigUtil.CORE_AUTOCRLF);
    String value = result.getOutputAsJoinedString();
    return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("input");
  }

  public boolean shouldWarn() {
    return myShouldWarn;
  }

}
