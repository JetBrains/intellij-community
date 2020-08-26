// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.remote;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.exists;
import static git4idea.GitUtil.mention;
import static git4idea.i18n.GitBundle.message;

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
                               @NotNull @NlsSafe String initialRemoteName,
                               @NotNull @NlsSafe String initialRemoteUrl) {
    super(repository.getProject());
    myRepository = repository;
    myGit = git;
    myRemoteName = new JTextField(initialRemoteName, 30);
    myInitialRemoteName = initialRemoteName;
    myRemoteUrl = new JTextField(initialRemoteUrl, 30);
    setTitle(message("remotes.define.remote") + mention(myRepository));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    JPanel defineRemoteComponent = new JPanel(new GridBagLayout());
    GridBag gb = new GridBag().
      setDefaultAnchor(GridBagConstraints.LINE_START).
      setDefaultInsets(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, 0, 0).
      setDefaultFill(GridBagConstraints.HORIZONTAL);
    defineRemoteComponent
      .add(new JBLabel(message("remotes.define.remote.name") + " ", SwingConstants.RIGHT), gb.nextLine().next().weightx(0.0));
    defineRemoteComponent.add(myRemoteName, gb.next().weightx(1.0));
    defineRemoteComponent
      .add(new JBLabel(message("remotes.define.remote.url") + " ", SwingConstants.RIGHT), gb.nextLine().next().weightx(0.0));
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
      Messages.showErrorDialog(myRepository.getProject(), XmlStringUtil.wrapInHtml(error), message("remotes.define.invalid.remote"));
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
      return new ValidationInfo(message("remotes.define.empty.remote.name.validation.message"), myRemoteName);
    }
    if (getRemoteUrl().isEmpty()) {
      return new ValidationInfo(message("remotes.define.empty.remote.url.validation.message"), myRemoteUrl);
    }
    if (!GitRefNameValidator.getInstance().checkInput(name)) {
      return new ValidationInfo(message("remotes.define.invalid.remote.name.validation.message"), myRemoteName);
    }
    if (!name.equals(myInitialRemoteName) && exists(myRepository.getRemotes(), remote -> remote.getName().equals(name))) {
      return new ValidationInfo(message("remotes.define.duplicate.remote.name.validation.message", name), myRemoteName);
    }
    return null;
  }

  @Nullable
  @Nls
  private String validateRemoteUnderModal(@NotNull final String url) throws ProcessCanceledException {
    GitCommandResult result = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      return myGit.lsRemote(myRepository.getProject(), virtualToIoFile(myRepository.getRoot()), url);
    }, message("remotes.define.checking.url.progress.message"), true, myRepository.getProject());
    return !result.success()
           ? message("remotes.define.remote.url.validation.fail.message") + " " + result.getErrorOutputAsHtmlString()
           : null;
  }
}
