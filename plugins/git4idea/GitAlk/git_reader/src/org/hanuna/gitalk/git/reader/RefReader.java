package org.hanuna.gitalk.git.reader;

import com.intellij.openapi.project.Project;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.git.reader.util.GitProcessFactory;
import org.hanuna.gitalk.git.reader.util.ProcessOutputReader;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class RefReader {
  private final List<Ref> refs = new ArrayList<Ref>();
  private final ProcessOutputReader outputReader;
  private Project myProject;
  private final boolean myReusePreviousGitOutput;

  private static List<Ref> ourPreviousOutput;

  private RefReader(@NotNull Executor<Integer> progressUpdater, Project project, boolean reusePreviousGitOutput) {
    myProject = project;
    myReusePreviousGitOutput = reusePreviousGitOutput;
    outputReader = new ProcessOutputReader(progressUpdater, new Executor<String>() {
      @Override
      public void execute(String key) {
        appendLine(key);
      }
    });
  }

  public RefReader(Project project, boolean reusePreviousGitOutput) {
    this(new Executor<Integer>() {
      @Override
      public void execute(Integer key) {

      }
    }, project, reusePreviousGitOutput);
  }

  private void appendLine(@NotNull String line) {
    refs.addAll(RefParser.parseCommitRefs(line));
  }

  @NotNull
  public List<Ref> readAllRefs() throws GitException, IOException {
    if (myReusePreviousGitOutput && ourPreviousOutput != null) {
      return ourPreviousOutput;
    }

    Process process = GitProcessFactory.getInstance(myProject).refs();
    outputReader.startRead(process);
    List<Ref> refs1 = refs;
    ourPreviousOutput = refs1;
    return refs1;
  }


}
