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

import static git4idea.commands.GitAuthenticationMode.FULL;

class GitNativeSshGuiAuthenticator implements GitNativeSshAuthenticator {
  @NotNull private final Project myProject;
  @NotNull private final GitAuthenticationGate myAuthenticationGate;
  @NotNull private final GitAuthenticationMode myAuthenticationMode;
  private final boolean myDoNotRememberPasswords;

  @Nullable private String myLastAskedKeyPath = null;
  @Nullable private String myLastAskedUserName = null;

  GitNativeSshGuiAuthenticator(@NotNull Project project,
                               @NotNull GitAuthenticationGate authenticationGate,
                               @NotNull GitAuthenticationMode authenticationMode,
                               boolean doNotRememberPasswords) {
    myProject = project;
    myAuthenticationGate = authenticationGate;
    myAuthenticationMode = authenticationMode;
    myDoNotRememberPasswords = doNotRememberPasswords;
  }

  @Nullable
  @Override
  public String handleInput(@NotNull String description) {
    if(myAuthenticationMode == GitAuthenticationMode.NONE) return null;
    return myAuthenticationGate.waitAndCompute(() -> {
      if (isKeyPassphrase(description)) return askKeyPassphraseInput(description);
      if (isSshPassword(description)) return askSshPasswordInput(description);
      if (isConfirmation(description)) return askConfirmationInput(description);

      return askGenericInput(description);
    });
  }


  private static boolean isKeyPassphrase(@NotNull String description) {
    return SSHUtil.PASSPHRASE_PROMPT.matcher(description).matches();
  }

  @Nullable
  private String askKeyPassphraseInput(@NotNull String description) {
    Matcher matcher = SSHUtil.PASSPHRASE_PROMPT.matcher(description);
    if (!matcher.matches()) throw new IllegalStateException(description);
    String keyPath = matcher.group(1);

    boolean resetPassword = keyPath.equals(myLastAskedKeyPath);
    myLastAskedKeyPath = keyPath;

    if (myDoNotRememberPasswords) {
      return askUser(() -> {
        String message = GitBundle.message("ssh.ask.passphrase.message", PathUtil.getFileName(keyPath));
        return Messages.showPasswordDialog(myProject, message, GitBundle.getString("ssh.ask.passphrase.title"), null);
      });
    }
    else {
      return GitSSHGUIHandler.askPassphrase(myProject, keyPath, resetPassword, myAuthenticationMode, null);
    }
  }

  private static boolean isSshPassword(@NotNull String description) {
    return SSHUtil.PASSWORD_PROMPT.matcher(description).matches();
  }

  @Nullable
  private String askSshPasswordInput(@NotNull String description) {
    Matcher matcher = SSHUtil.PASSWORD_PROMPT.matcher(description);
    if (!matcher.matches()) throw new IllegalStateException(description);
    String username = matcher.group(1);

    boolean resetPassword = username.equals(myLastAskedUserName);
    myLastAskedUserName = username;

    if (myDoNotRememberPasswords) {
      return askUser(() -> {
        String message = GitBundle.message("ssh.password.message", username);
        return Messages.showPasswordDialog(myProject, message, GitBundle.getString("ssh.password.title"), null);
      });
    }
    else {
      return GitSSHGUIHandler.askPassword(myProject, username, resetPassword, myAuthenticationMode, null);
    }
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
      return Messages.showPasswordDialog(myProject, description, GitBundle.message("ssh.keyboard.interactive.title"), null);
    });
  }

  @Nullable
  private String askUser(@NotNull Computable<String> query) {
    if (myAuthenticationMode != FULL) return null;

    Ref<String> answerRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      answerRef.set(query.compute());
    }, ModalityState.any());
    return answerRef.get();
  }
}
