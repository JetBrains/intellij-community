// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ssh.SSHUtil;
import com.intellij.util.PathUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

class GitNativeSshGuiAuthenticator implements GitNativeSshAuthenticator {
  private final Project myProject;
  private final boolean myIgnoreAuthenticationRequest;

  public GitNativeSshGuiAuthenticator(Project project, boolean isIgnoreAuthenticationRequest) {
    myProject = project;
    myIgnoreAuthenticationRequest = isIgnoreAuthenticationRequest;
  }

  @Nullable
  @Override
  public String askPassphrase(@NotNull String description) {
    if (isKeyPassphrase(description)) return askKeyPassphraseInput(description);
    if (isConfirmation(description)) return askConfirmationInput(description);

    return askGenericInput(description);
  }


  private static boolean isKeyPassphrase(@NotNull String description) {
    return SSHUtil.PASSPHRASE_PROMPT.matcher(description).matches();
  }

  @Nullable
  private String askKeyPassphraseInput(@NotNull String description) {
    return askUser(() -> {
      Matcher matcher = SSHUtil.PASSPHRASE_PROMPT.matcher(description);
      assert matcher.matches();
      String keyPath = matcher.group(1);

      String message = GitBundle.message("ssh.ask.passphrase.message", PathUtil.getFileName(keyPath));
      return Messages.showInputDialog(myProject, message, GitBundle.getString("ssh.ask.passphrase.title"), null, null, null);
    });
  }

  private static boolean isConfirmation(@NotNull String description) {
    return description.contains(SSHUtil.CONFIRM_CONNECTION_PROMPT);
  }

  @Nullable
  private String askConfirmationInput(@NotNull String description) {
    return askUser(() -> {
      String message = StringUtil.replace(description,
                                          SSHUtil.CONFIRM_CONNECTION_PROMPT + " (yes/no)?",
                                          SSHUtil.CONFIRM_CONNECTION_PROMPT + "?");

      int answer = Messages.showYesNoDialog(myProject, message, "SSH Confirmation", null);
      if (answer == Messages.YES) {
        return "yes";
      }
      else if (answer == Messages.NO) {
        return "no";
      }
      else {
        return null;
      }
    });
  }

  @Nullable
  private String askGenericInput(@NotNull String description) {
    return askUser(() -> {
      return Messages.showInputDialog(myProject, description, GitBundle.message("ssh.keyboard.interactive.title"), null, null, null);
    });
  }

  @Nullable
  private String askUser(@NotNull Computable<String> query) {
    if (myIgnoreAuthenticationRequest) return null;

    Ref<String> answerRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      answerRef.set(query.compute());
    }, ModalityState.any());
    return answerRef.get();
  }
}
