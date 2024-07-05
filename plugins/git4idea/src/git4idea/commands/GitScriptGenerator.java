// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.externalProcessAuthHelper.ScriptGeneratorImpl;
import com.intellij.openapi.util.text.StringUtil;
import externalApp.ExternalApp;
import externalApp.nativessh.NativeSshAskPassAppHandler;
import git4idea.commit.signing.PinentryService;
import git4idea.config.GitExecutable;
import git4idea.editor.GitRebaseEditorAppHandler;
import git4idea.http.GitAskPassAppHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class GitScriptGenerator extends ScriptGeneratorImpl {
  private final GitExecutable myExecutable;

  public GitScriptGenerator(@NotNull GitExecutable executable) {
    myExecutable = executable;
  }

  @Override
  protected @NotNull String getJavaExecutablePath() {
    if (myExecutable instanceof GitExecutable.Wsl) {
      File javaExecutable = new File(String.format("%s\\bin\\java.exe", System.getProperty("java.home")));
      return myExecutable.convertFilePath(javaExecutable);
    }
    return super.getJavaExecutablePath();
  }

  @Override
  public @NotNull String commandLine(@NotNull Class<? extends ExternalApp> mainClass, boolean useBatchFile) {
    String commandLine = super.commandLine(mainClass, useBatchFile);

    if (myExecutable instanceof GitExecutable.Wsl) {
      // pass ENV variables from git to java command
      StringBuilder sb = new StringBuilder();
      List<String> envs = List.of(
        NativeSshAskPassAppHandler.IJ_SSH_ASK_PASS_HANDLER_ENV,
        NativeSshAskPassAppHandler.IJ_SSH_ASK_PASS_PORT_ENV,
        GitAskPassAppHandler.IJ_ASK_PASS_HANDLER_ENV,
        GitAskPassAppHandler.IJ_ASK_PASS_PORT_ENV,
        GitRebaseEditorAppHandler.IJ_EDITOR_HANDLER_ENV,
        GitRebaseEditorAppHandler.IJ_EDITOR_PORT_ENV,
        PinentryService.PINENTRY_USER_DATA_ENV);
      sb.append("export WSLENV=");
      sb.append(StringUtil.join(envs, it -> it + "/w", ":"));
      sb.append("\n");

      sb.append(commandLine);
      return sb.toString();
    }
    return commandLine;
  }
}
