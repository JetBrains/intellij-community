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
import git4idea.repo.GitRemote;
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

  private final Project myProject;
  private final GitRemote myRemote;
  private boolean myCancelled;

  public GitHttpCredentialsProvider(@NotNull Project project, @NotNull GitRemote remote) {
    myProject = project;
    myRemote = remote;
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
      String url = myRemote.getFirstUrl();
      if (url == null) {
        return false;
      }
      String username = getUserNameFromUrl(url);
      final AuthDialog dialog = new AuthDialog(myProject, "Login required", "Login to " + url, username, null, true);
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          dialog.show();
        }
      });
      boolean ok = dialog.isOK();
      if (!ok) {
        myCancelled = true;
      } else {
        if (userNameItem != null) {
          userNameItem.setValue(dialog.getUsername());
        }
        if (passwordItem != null) {
          passwordItem.setValue(dialog.getPassword().toCharArray());
        }
      }
      return ok;
    }
    return true;
  }

  private static final Pattern HTTP_URL_PATTERN = Pattern.compile("http(?:s?)://(?:([\\S^@\\.]*)@)?.*");
  
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
