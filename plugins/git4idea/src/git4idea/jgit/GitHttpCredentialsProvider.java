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
package git4idea.jgit;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.AuthDialog;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Kirill Likhodedov
 */
public class GitHttpCredentialsProvider extends CredentialsProvider {

  private static final Pattern HTTP_URL_PATTERN = Pattern.compile("http(?:s?)://(?:([\\S^@\\.]*)@)?.*");

  private final Project myProject;
  private String myRemoteUrl;

  private boolean myCancelled;
  private boolean myRememberPassword;
  private String myPassword;
  private String myUserName;
  private boolean myShowDialog;
  private boolean myDialogShown;

  public GitHttpCredentialsProvider(@NotNull Project project, @NotNull String remoteUrl) {
    myProject = project;
    myRemoteUrl = remoteUrl;
  }

  @Override
  public boolean isInteractive() {
    return true;
  }

  @Override
  public boolean supports(CredentialItem... items) {
    for (CredentialItem item : items) {
      if (item instanceof CredentialItem.Password) {
        continue;
      }
      if (item instanceof CredentialItem.Username) {
        continue;
      }
      return false;
    }
    return true;
  }

  @Override
  public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
    CredentialItem.Username userNameItem = null;
    CredentialItem.Password passwordItem = null;
    for (CredentialItem item : items) {
      if (item instanceof CredentialItem.Username) {
        userNameItem = (CredentialItem.Username)item;
      } else if (item instanceof CredentialItem.Password) {
        passwordItem = (CredentialItem.Password)item;
      }
    }
    
    if (userNameItem != null || passwordItem != null) {
      String username = getUserNameFromUrl(myRemoteUrl);
      String password = null;
      if (username == null) { // username is not in the url => reading pre-filled value from the password storage
        username = myUserName;
        password = myPassword;
      } else if (username.equals(myUserName)) { // username is in url => read password only if it is for the same user
        password = myPassword;
      }

      boolean rememberPassword = myRememberPassword;
      boolean ok;
      if (username != null && password != null && !myShowDialog) {
        ok = true;
        myDialogShown = false;
      } else {
        final AuthDialog dialog = new AuthDialog(myProject, "Login required", "Login to " + myRemoteUrl, username, password, false);
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            dialog.show();
          }
        });
        ok = dialog.isOK();
        myDialogShown = true;
        if (ok) {
          username = dialog.getUsername();
          password = dialog.getPassword();
          rememberPassword = dialog.isRememberPassword();
        }
      }

      if (ok) {
        if (userNameItem != null) {
          userNameItem.setValue(username);
        }
        if (passwordItem != null) {
          passwordItem.setValue(password.toCharArray());
        }
        myRememberPassword = rememberPassword;
        myPassword = password;
        myUserName = username;
      }
      else {
        myCancelled = true;
        myRememberPassword = false;  // in case of re-usage of the provider
      }
      return ok;
    }
    return true;
  }

  public boolean isRememberPassword() {
    return myRememberPassword;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  public String getPassword() {
    return myPassword;
  }

  @Nullable
  public String getUserName() {
    return myUserName;
  }

  @NotNull
  public String getUrl() {
    return myRemoteUrl;
  }
  
  public void setUrl(@NotNull String url) {
    myRemoteUrl = url;
  }

  public void fillAuthDataIfNotFilled(@NotNull String login, @Nullable String password) {
    if (myUserName == null) {
      myUserName = login;
      myPassword = password;
    } else if (myPassword != null) {
      myPassword = password;
    }
  }

  public void setAlwaysShowDialog(boolean showDialog) {
    myShowDialog = showDialog;
  }

  public boolean wasDialogShown() {
    return myDialogShown;
  }

  @Nullable
  private static String getUserNameFromUrl(@NotNull String url) {
    Matcher matcher = HTTP_URL_PATTERN.matcher(url);
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return null;
  }

  public boolean wasCancelled() {
    return myCancelled;
  }
}
