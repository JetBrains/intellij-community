/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.github;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jetbrains.plugins.github.ui.GitHubCreateGistDialog;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author oleg
 * @date 9/27/11
 */
public class GithubCreateGistAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GithubCreateGistAction.class);

  protected GithubCreateGistAction() {
    super("Create Gist...", "Create github gist", GithubUtil.GITHUB_ICON);
  }

  @Override
  public void update(final AnActionEvent e) {
    final long startTime = System.nanoTime();
    try {
      final Project project = e.getData(PlatformDataKeys.PROJECT);
      if (project == null || project.isDefault()) {
        e.getPresentation().setVisible(false);
        e.getPresentation().setEnabled(false);
        return;
      }
      final Editor editor = e.getData(PlatformDataKeys.EDITOR);
      if (editor == null){
        e.getPresentation().setVisible(false);
        e.getPresentation().setEnabled(false);
        return;
      }
      if (!editor.getSelectionModel().hasSelection()){
        e.getPresentation().setVisible(false);
        e.getPresentation().setEnabled(false);
        return;
      }
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(true);
    }
    finally {
      if (LOG.isDebugEnabled()) {
        LOG.debug("GithubCreateGistAction#update finished in: " + (System.nanoTime() - startTime) / 10e6 + "ms");
      }
    }
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null || project.isDefault()) {
      return;
    }
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (editor == null){
      return;
    }
    final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (file == null) {
      return;
    }
    final boolean useGitHubAccount;
    if (!GithubUtil.checkCredentials(project)) {
      final GithubLoginDialog dialog = new GithubLoginDialog(project);
      dialog.show();
      useGitHubAccount = GithubUtil.checkCredentials(project);
    } else {
      useGitHubAccount = true;
    }

    // Ask for description and other params
    final GitHubCreateGistDialog dialog = new GitHubCreateGistDialog(project, useGitHubAccount);
    dialog.show();
    if (!dialog.isOK()){
      return;
    }
    final GithubSettings settings = GithubSettings.getInstance();
    final String password = settings.getPassword();
    final Ref<String> url = new Ref<String>();
    final String description = dialog.getDescription();
    final boolean isPrivate = dialog.isPrivate();
    final boolean anonymous = dialog.isAnonymous();
    final boolean openInBrowser = dialog.isOpenInBrowser();
    
    // Text
    final String text = editor.getSelectionModel().getSelectedText();
    
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        final HttpClient client = anonymous ? GithubUtil.getHttpClient(null, null) : GithubUtil.getHttpClient(settings.getLogin(), password);
        final PostMethod method = new PostMethod("https://gist.github.com/gists");
        method.addParameters(new NameValuePair[]{
          new NameValuePair("description", description),
          new NameValuePair("file_ext[gistfile1]", "." + file.getExtension()),
          new NameValuePair("file_name[gistfile1]", file.getNameWithoutExtension()),
          new NameValuePair("file_contents[gistfile1]", text)
        });
        if (isPrivate){
          method.addParameter("action_button", "private");
        }
        try {
          client.executeMethod(method);
          final String responce = method.getResponseBodyAsString();
          // TODO[oleg] fix it when github API v3 becomes public
          // http://developer.github.com/v3/gists/
          final Matcher matcher = Pattern.compile("href=\"[^\"]*").matcher(responce);
          matcher.find();
          url.set(matcher.group().substring(6));
        }
        catch (IOException e1) {
          LOG.error("Failed to create gist: " + e1);
          return;
        }
        finally {
          method.releaseConnection();
        }
      }
    }, "Communicating With GitHub", false, project);
    if (url.isNull()){
      return;
    }
    if (openInBrowser) {
      BrowserUtil.launchBrowser(url.get());
    } else {
      Messages.showInfoMessage(project, "Your gist url: " + url.get(), "Gist Created Successfully");
    }
  }
}
