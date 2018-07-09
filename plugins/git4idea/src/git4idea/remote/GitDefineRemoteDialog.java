/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.remote;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
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

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.exists;
import static git4idea.GitUtil.mention;

public class GitDefineRemoteDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitDefineRemoteDialog.class);

  @NotNull private final GitRepository myRepository;
  @NotNull private final Git myGit;

  @NotNull private final String myInitialRemoteName;
  @NotNull private final JTextField myRemoteName;
  @NotNull private final JTextField myRemoteUrl;

  public GitDefineRemoteDialog(@NotNull GitRepository repository, @NotNull Git git) {
    this(repository, git, GitRemote.ORIGIN, "");
  }

  public GitDefineRemoteDialog(@NotNull GitRepository repository,
                               @NotNull Git git,
                               @NotNull String initialRemoteName,
                               @NotNull String initialRemoteUrl) {
    super(repository.getProject());
    myRepository = repository;
    myGit = git;
    myRemoteName = new JTextField(initialRemoteName, 30);
    myInitialRemoteName = initialRemoteName;
    myRemoteUrl = new JTextField(initialRemoteUrl, 30);
    setTitle("Define Remote" + mention(myRepository));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return null;
  };

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    JPanel defineRemoteComponent = new JPanel(new GridBagLayout());
    GridBag gb = new GridBag().
      setDefaultAnchor(GridBagConstraints.LINE_START).
      setDefaultInsets(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, 0, 0).
      setDefaultFill(GridBagConstraints.HORIZONTAL);
    defineRemoteComponent.add(new JBLabel("Name: ", SwingConstants.RIGHT), gb.nextLine().next().weightx(0.0));
    defineRemoteComponent.add(myRemoteName, gb.next().weightx(1.0));
    defineRemoteComponent.add(new JBLabel("URL: ", SwingConstants.RIGHT), gb.nextLine().next().weightx(0.0));
    defineRemoteComponent.add(myRemoteUrl, gb.next().weightx(1.0));
    return defineRemoteComponent;
  }

  @NotNull
  public String getRemoteName() {
    return StringUtil.notNullize(myRemoteName.getText()).trim();
  }

  @NotNull
  public String getRemoteUrl() {
    return StringUtil.notNullize(myRemoteUrl.getText()).trim();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRemoteName.getText().isEmpty() ? myRemoteName : myRemoteUrl;
  }

  @Override
  protected void doOKAction() {
    String url = getRemoteUrl();
    String error = validateRemoteUnderModal(url);
    if (error != null) {
      LOG.warn(String.format("Invalid remote. Name: [%s], URL: [%s], error: %s", getRemoteName(), url, error));
      Messages.showErrorDialog(myRepository.getProject(), error, "Invalid Remote");
    }
    else {
      super.doOKAction();
    }
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String name = getRemoteName();
    if (name.isEmpty()) {
      return new ValidationInfo("Remote name can't be empty", myRemoteName);
    }
    if (getRemoteUrl().isEmpty()) {
      return new ValidationInfo("Remote URL can't be empty", myRemoteUrl);
    }
    if (!GitRefNameValidator.getInstance().checkInput(name)) {
      return new ValidationInfo("Remote name contains illegal characters", myRemoteName);
    }
    if (!name.equals(myInitialRemoteName) && exists(myRepository.getRemotes(), remote -> remote.getName().equals(name))) {
      return new ValidationInfo("Remote name '" + name + "' is already in use", myRemoteName);
    }
    return null;
  }

  @Nullable
  private String validateRemoteUnderModal(@NotNull final String url) throws ProcessCanceledException {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      GitCommandResult result = myGit.lsRemote(myRepository.getProject(), virtualToIoFile(myRepository.getRoot()), url);
      return !result.success() ? "Remote URL test failed: " + result.getErrorOutputAsHtmlString() : null;
    }, "Checking URL...", true, myRepository.getProject());
  }

}
