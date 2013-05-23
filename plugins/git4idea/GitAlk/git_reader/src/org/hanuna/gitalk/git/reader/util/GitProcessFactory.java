package org.hanuna.gitalk.git.reader.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import git4idea.repo.GitRepository;
import gitlog.GitLogComponent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * @author erokhins
 */
public class GitProcessFactory {
  private final static String DEFAULT_LOG_REQUEST = "git log --all --date-order --sparse --encoding=UTF-8 --full-history";
  private final static String COMMIT_PARENTS_LOG_FORMAT = "--format=%h|-%p";
  private final static String TIMESTAMP_COMMIT_PARENTS_LOG_FORMAT = "--format=%ct|-%h|-%p";
  private final static String COMMIT_DATA_LOG_FORMAT = "--format=%h|-%an|-%at|-%s";
  private final Project myProject;

  public GitProcessFactory(Project project) {
    myProject = project;
  }

  public static GitProcessFactory getInstance(Project project) {
    return new GitProcessFactory(project);
  }

  public Process commitData(@NotNull String commitHash) throws IOException {
    String request = "git log " + commitHash + " --no-walk " + COMMIT_DATA_LOG_FORMAT;
    return request(request);
  }

  public Process commitDatas(@NotNull String commitHashes) throws IOException {
    String request = "git log " + commitHashes + " --no-walk " + COMMIT_DATA_LOG_FORMAT;
    return request(request);
  }

  public Process allLog() throws IOException {
    String request = "git log --all --date-order " + COMMIT_PARENTS_LOG_FORMAT;
    return request(request);
  }

  public Process firstPart(int maxCount) throws IOException {
    String request = DEFAULT_LOG_REQUEST + " --max-count=" + maxCount + " " + TIMESTAMP_COMMIT_PARENTS_LOG_FORMAT;
    return request(request);
  }

  public Process logPart(long startTimestamp, int maxCount) throws IOException {
    String request = "git log --before=" + startTimestamp + " --all --max-count=" + maxCount + " --date-order" +
                     " --pretty=format:%ct|-%h|-%p --encoding=UTF-8 --full-history --sparse";
    System.out.println(request);
    return request(request);
  }


  public Process refs() throws IOException {
    String request = "git log --all --no-walk --format=%h%d --decorate=full ";
    return request(request);
  }

  private Process request(String request) throws IOException {
    GitRepository repository = ServiceManager.getService(myProject, GitLogComponent.class).getRepository();
    return Runtime.getRuntime().exec(request, ArrayUtil.EMPTY_STRING_ARRAY, new File(repository.getRoot().getPath()));
  }

}
