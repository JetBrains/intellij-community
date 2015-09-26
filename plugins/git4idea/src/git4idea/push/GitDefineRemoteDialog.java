/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.push;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class GitDefineRemoteDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitDefineRemoteDialog.class);

  @NotNull private final GitRepository myRepository;
  @NotNull private final Git myGit;

  @NotNull private final JTextField myRemoteName;
  @NotNull private final JTextField myRemoteUrl;

  GitDefineRemoteDialog(@NotNull GitRepository repository, @NotNull Git git) {
    super(repository.getProject());
    myRepository = repository;
    myGit = git;
    myRemoteName = new JTextField(GitRemote.ORIGIN_NAME, 20);
    myRemoteUrl = new JTextField(20);
    setTitle("Define Remote");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel defineRemoteComponent = new JPanel(new GridBagLayout());
    GridBag gb = new GridBag().
      setDefaultAnchor(GridBagConstraints.LINE_START).
      setDefaultInsets(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, 0, 0);
    defineRemoteComponent.add(new JBLabel("Name:"), gb.nextLine().next().anchor(GridBagConstraints.EAST));
    defineRemoteComponent.add(myRemoteName, gb.next());
    defineRemoteComponent.add(new JBLabel("URL: "),
                              gb.nextLine().next().anchor(GridBagConstraints.EAST).insets(0, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP, 0));
    defineRemoteComponent.add(myRemoteUrl, gb.next());
    return defineRemoteComponent;
  }

  @NotNull
  String getRemoteName() {
    return StringUtil.notNullize(myRemoteName.getText()).trim();
  }

  @NotNull
  String getRemoteUrl() {
    return StringUtil.notNullize(myRemoteUrl.getText()).trim();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRemoteUrl;
  }

  @Override
  protected void doOKAction() {
    String name = getRemoteName();
    String url = getRemoteUrl();
    String error = validateRemoteUnderModal(name, url);
    if (error != null) {
      LOG.warn(String.format("Invalid remote. Name: [%s], URL: [%s], error: %s", name, url, error));
      Messages.showErrorDialog(myRepository.getProject(), error, "Invalid Remote");
    }
    else {
      super.doOKAction();
    }
  }

  @Nullable
  private String validateRemoteUnderModal(@NotNull String name, @NotNull final String url) throws ProcessCanceledException {
    if (url.isEmpty()) {
      return "URL can't be empty";
    }
    if (!GitRefNameValidator.getInstance().checkInput(name)) {
      return "Remote name contains illegal characters";
    }

    return ProgressManager.getInstance().runProcessWithProgressSynchronously(new ThrowableComputable<String, ProcessCanceledException>() {
      @Override
      public String compute() throws ProcessCanceledException {
        final GitCommandResult result = myGit.lsRemote(myRepository.getProject(), VfsUtilCore.virtualToIoFile(myRepository.getRoot()), url);
        return !result.success() ? "Remote URL test failed: " + result.getErrorOutputAsHtmlString() : null;
      }
    }, "Checking URL...", true, myRepository.getProject());
  }

}
