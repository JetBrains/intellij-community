package git4idea.test;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.Executor;
import git4idea.repo.GitRepository;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static git4idea.test.GitExecutor.*;

/**
 * Create popular scenarios used in multiple tests, for example:
 * - create a branch and commit something there;
 * - make some unmerged files in the working tree;
 * - make the situation when local changes would be overwritten by merge.
 */
public class GitScenarios {
  private static final String BRANCH_FOR_UNMERGED_CONFLICTS = "unmerged_files_branch_" + Math.random();

  public static final MergeContent LOCAL_CHANGES_OVERWRITTEN_BY = new MergeContent("common content\ncommon content\ncommon content\n",
                                                                                    "line with branch changes\n",
                                                                                    "line with master changes");

  /**
   * Create a branch with a commit and return back to master.
   */
  public static void branchWithCommit(GitRepository repository, String name, String file, String content, boolean returnToMaster) {
    cd(repository);
    git(repository, "checkout -b " + name);
    Executor.touch(file, content);
    git(repository, "add " + file);
    git(repository, "commit -m branch_content");

    if (returnToMaster) {
      git(repository, "checkout master");
    }
  }

  /**
   * Create a branch with a commit and return back to master.
   */
  public static void branchWithCommit(GitRepository repository, String name, String file, String content) {
    GitScenarios.branchWithCommit(repository, name, file, content, true);
  }

  /**
   * Create a branch with a commit and return back to master.
   */
  public static void branchWithCommit(GitRepository repository, String name) {
    GitScenarios.branchWithCommit(repository, name, "branch_file.txt", "branch content", true);
  }

  /**
   * Create a branch with a commit and return back to master.
   */
  public static void branchWithCommit(Collection<GitRepository> repositories, final String name, final String file, final String content) {
    for (GitRepository repository : repositories) {
      branchWithCommit(repository, name, file, content);
    }
  }

  /**
   * Create a branch with a commit and return back to master.
   */
  public static void branchWithCommit(Collection<GitRepository> repositories, String name) {
    GitScenarios.branchWithCommit(repositories, name, "branch_file.txt", "branch content");
  }

  /**
   * Make an unmerged file in the repository.
   */
  public static String unmergedFiles(GitRepository repository) {
    conflict(repository, BRANCH_FOR_UNMERGED_CONFLICTS, "unmerged.txt");
    git(repository, "merge " + BRANCH_FOR_UNMERGED_CONFLICTS, true);
    return git(repository, "branch -D " + BRANCH_FOR_UNMERGED_CONFLICTS);
  }

  /**
   * Creates a branch with the given name, and produces conflicted content in a file between this branch and master.
   * Branch must not exist at this point.
   */
  public static String conflict(GitRepository repository, String branch, String file) {
    assert !branchExists(repository, branch) : "Branch [" + branch + "] shouldn\'t exist for this scenario";

    cd(repository);

    Executor.touch(file, "initial content");
    git(repository, "add " + file);
    git(repository, "commit -m initial_content");

    git(repository, "checkout -b " + branch);
    Executor.echo(file, "branch content");
    git(repository, "commit -am branch_content");

    git(repository, "checkout master");
    Executor.echo(file, "master content");
    return git(repository, "commit -am master_content");
  }

  /**
   * Creates a branch with the given name, and produces conflicted content in a file between this branch and master.
   * Branch must not exist at this point.
   */
  public static String conflict(GitRepository repository, String branch) {
    return GitScenarios.conflict(repository, branch, "conflict.txt");
  }

  /**
   * Create an untracked file in master and a tracked file with the same name in the branch.
   * This produces the "some untracked files would be overwritten by..." error when trying to checkout or merge.
   * Branch with the given name shall exist.
   */
  public static void untrackedFileOverwrittenBy(GitRepository repository, String branch, Collection<String> fileNames) {
    cd(repository);
    git(repository, "checkout " + branch);

    for (String it : fileNames) {
      Executor.touch(it, "branch content");
      git(repository, "add " + it);
    }


    git(repository, "commit -m untracked_files");
    git(repository, "checkout master");

    for (String it : fileNames) {
      Executor.touch(it, "master content");
    }
  }

  /**
   * Creates a file in both master and branch so that the content differs, but can be merged without conflicts.
   * That way, git checkout/merge will fail with "local changes would be overwritten by checkout/merge",
   * but smart checkout/merge (stash-checkout/merge-unstash) would succeed without conflicts.
   * <p/>
   * NB: the branch should not exist before this is called!
   */
  public static void localChangesOverwrittenByWithoutConflict(GitRepository repository, String branch, Collection<String> fileNames) {
    cd(repository);

    for (String it : fileNames) {
      Executor.echo(it, LOCAL_CHANGES_OVERWRITTEN_BY.initial);
      git(repository, "add " + it);
    }

    git(repository, "commit -m initial_changes");

    git(repository, "checkout -b " + branch);
    for (String it : fileNames) {
      prepend(it, LOCAL_CHANGES_OVERWRITTEN_BY.branchLine);
      git(repository, "add " + it);
    }

    git(repository, "commit -m branch_changes");

    git(repository, "checkout master");
    for (String it : fileNames) {
      append1(it, LOCAL_CHANGES_OVERWRITTEN_BY.masterLine);
    }
  }

  public static void append1(String fileName, String content) {
    Executor.echo(fileName, content);
  }

  public static void prepend(String fileName, final String content) {
    String previousContent = Executor.cat(fileName);
    try {
      FileUtil.writeToFile(new File(Executor.pwd(), fileName), content + previousContent);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String commit(GitRepository repository, String file) {
    cd(repository);
    Executor.touch(file);
    git(repository, "add " + file);
    return git(repository, "commit -m just_a_commit");
  }

  public static String commit(GitRepository repository) {
    return GitScenarios.commit(repository, "just_a_file_" + String.valueOf(Math.random()) + ".txt");
  }

  public static boolean branchExists(GitRepository repo, String branch) {
    return git(repo, "branch").contains(branch);
  }

  public static class MergeContent {
    public final String initial;
    public final String branchLine;
    public final String masterLine;
    public MergeContent(String initialContent, String branchLine, String masterLine) {

      initial = initialContent;
      this.branchLine = branchLine;
      this.masterLine = masterLine;
    }
  }
}
