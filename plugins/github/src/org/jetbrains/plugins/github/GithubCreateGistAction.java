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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.Notificator;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GitHubCreateGistDialog;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;

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
        // Using GitHub Gist API v3: http://developer.github.com/v3/gists/
        final HttpClient client = anonymous ? GithubUtil.getHttpClient(null, null) : GithubUtil.getHttpClient(settings.getLogin(), password);
        final PostMethod method = new PostMethod("https://api.github.com/gists");

        String request = prepareJsonRequest(description, isPrivate, text, file);

        String response;
        try {
          method.setRequestEntity(new StringRequestEntity(request, "application/json", "UTF-8"));
          client.executeMethod(method);
          response = method.getResponseBodyAsString();
        }
        catch (IOException e1) {
          showError(project, "Failed to create gist", "", null, e1);
          return;
        }
        finally {
          method.releaseConnection();
        }

        JsonObject jsonResponse;
        try {
          jsonResponse = new JsonParser().parse(response).getAsJsonObject();
        }
        catch (JsonSyntaxException jse) {
          showError(project, "Couldn't parse GitHub response", "", response, jse);
          return;
        }

        JsonElement htmlUrl = jsonResponse.get("html_url");
        if (htmlUrl == null) {
          showError(project, "Invalid GitHub response", "No html_url property", response, null);
          return;
        }
        url.set(htmlUrl.getAsString());
      }
    }, "Communicating With GitHub", false, project);

    if (url.isNull()){
      return;
    }
    if (openInBrowser) {
      BrowserUtil.launchBrowser(url.get());
    } else {
      Notificator.getInstance(project).notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, "Gist Created Successfully",
                                              "Your gist url: <a href='open'>" + url.get() + "</a>", NotificationType.INFORMATION,
                                              new NotificationListener() {
                                                @Override
                                                public void hyperlinkUpdate(@NotNull Notification notification,
                                                                            @NotNull HyperlinkEvent event) {
                                                  if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                                    BrowserUtil.launchBrowser(url.get());
                                                  }
                                                }
                                              });
    }
  }

  private static void showError(@NotNull Project project, @NotNull String title, @NotNull String content,
                                @Nullable String details, @Nullable Exception e) {
    Notificator.getInstance(project).notifyError(title, content);
    LOG.info("Couldn't parse response as json data: \n" + content + "\n" + details, e);
  }

  private static String prepareJsonRequest(String description, boolean isPrivate, String text, VirtualFile file) {
    JsonObject json = new JsonObject();
    json.addProperty("description", description);
    json.addProperty("public", Boolean.toString(!isPrivate));
    JsonObject file1 = new JsonObject();
    file1.addProperty("content", text);
    JsonObject files = new JsonObject();
    files.add(file.getName(), file1);
    json.add("files", files);
    return json.toString();
  }

}
