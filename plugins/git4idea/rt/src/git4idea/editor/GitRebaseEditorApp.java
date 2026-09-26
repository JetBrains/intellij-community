// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editor;

import externalApp.ExternalApp;
import externalApp.ExternalAppEntry;
import externalApp.ExternalAppUtil;
import externalApp.ExternalCli;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;

import static git4idea.editor.GitRebaseEditorAppHandler.ERROR_EXIT_CODE;

/**
 * The rebase editor application, this editor is invoked by the git.
 */
public class GitRebaseEditorApp implements ExternalApp, ExternalCli {
  /**
   * Visible replacement character inserted in case of encoding problems.
   */
  private static final @NotNull Character REPLACEMENT_CHARACTER = '\uFFFD';

  @Override
  public int entryPoint(ExternalAppEntry entry) {
    try {
      if (entry.getArgs().length != 1) {
        entry.getStderr().println("Invalid arguments: " + Arrays.asList(entry.getArgs()));
        return ERROR_EXIT_CODE;
      }

      String handlerId = ExternalAppUtil.getEnv(GitRebaseEditorAppHandler.IJ_EDITOR_HANDLER_ENV, entry.getEnvironment());
      int idePort = ExternalAppUtil.getEnvInt(GitRebaseEditorAppHandler.IJ_EDITOR_PORT_ENV, entry.getEnvironment());

      String workingDir = entry.getWorkingDirectory();
      String path = entry.getArgs()[0];

      if (hasEncodingProblem(path) || hasEncodingProblem(workingDir)) {
        logEncodingProblem(entry, path, workingDir);
        return ERROR_EXIT_CODE;
      }

      String bodyContent = path + "\n" + workingDir;

      ExternalAppUtil.Result result = ExternalAppUtil.sendIdeRequest(GitRebaseEditorAppHandler.ENTRY_POINT_NAME, idePort,
                                                                     handlerId, bodyContent);

      if (result.isError) {
        entry.getStderr().println(result.getPresentableError());
        return ERROR_EXIT_CODE;
      }

      String response = result.response;
      if (response == null) {
        return ERROR_EXIT_CODE; // dialog cancelled
      }

      int exitCode = Integer.parseInt(response);
      return exitCode;
    }
    catch (Throwable t) {
      entry.getStderr().println(t.getMessage());
      t.printStackTrace(entry.getStderr());
      return ERROR_EXIT_CODE;
    }
  }

  /**
   * We force en_US.UTF-8 as a default locale. However, it can be missing in the system.
   * In this case if the to-do file path contains non-ASCII characters, they are replaced with {@link #REPLACEMENT_CHARACTER}.
   */
  private static void logEncodingProblem(ExternalAppEntry entry, String path, String workingDir) {
    entry.getStderr().printf("Path to rebase todo file is malformed - %s%nWorking dir - %s%n", path, workingDir);
    entry.getStderr().printf("Ensure that the default locale '%s' is registered in the system.%n%n", Locale.getDefault());
  }

  private static boolean hasEncodingProblem(String path) {
    return path.indexOf(REPLACEMENT_CHARACTER) != -1;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    var exitCode = new GitRebaseEditorApp().entryPoint(ExternalAppEntry.fromMain(args));
    System.exit(exitCode);
  }

}
