/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.notification.NotificationType;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.plugins.github.GithubCreateGistAction.NamedContent;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreateGistTest extends GithubCreateGistTestBase {
  public void testSimple() throws Throwable {
    List<NamedContent> expected = createContent();

    String url = GithubCreateGistAction.createGist(myProject, myGitHubSettings.getAuthData(), expected, true, "description", null);
    assertNotNull(url);
    GIST_ID = url.substring(url.lastIndexOf('/') + 1);

    checkGistExists();
    checkGistNotAnonymous();
    checkGistPrivate();
    checkGistDescription("description");
    checkGistContent(expected);
  }

  public void testAnonymous() throws Throwable {
    List<NamedContent> expected = createContent();

    String url = GithubCreateGistAction.createGist(myProject, null, expected, true, "description", null);
    assertNotNull(url);
    GIST_ID = url.substring(url.lastIndexOf('/') + 1);

    checkGistExists();
    checkGistAnonymous();
    checkGistPrivate();
    checkGistDescription("description");
    checkGistContent(expected);

    // anonymous gists - undeletable
    GIST_ID = null;
    GIST = null;
  }

  public void testUnusedFilenameField() throws Throwable {
    List<NamedContent> expected = createContent();

    String url = GithubCreateGistAction.createGist(myProject, myGitHubSettings.getAuthData(), expected, true, "description", "filename");
    assertNotNull(url);
    GIST_ID = url.substring(url.lastIndexOf('/') + 1);

    checkGistExists();
    checkGistNotAnonymous();
    checkGistPrivate();
    checkGistDescription("description");
    checkGistContent(expected);
  }

  public void testUsedFilenameField() throws Throwable {
    List<NamedContent> content = Collections.singletonList(new NamedContent("file.txt", "file.txt content"));
    List<NamedContent> expected = Collections.singletonList(new NamedContent("filename", "file.txt content"));

    String url = GithubCreateGistAction.createGist(myProject, myGitHubSettings.getAuthData(), content, true, "description", "filename");
    assertNotNull(url);
    GIST_ID = url.substring(url.lastIndexOf('/') + 1);

    checkGistExists();
    checkGistNotAnonymous();
    checkGistPrivate();
    checkGistDescription("description");
    checkGistContent(expected);
  }

  public void testPublic() throws Throwable {
    List<NamedContent> expected = createContent();

    String url = GithubCreateGistAction.createGist(myProject, myGitHubSettings.getAuthData(), expected, false, "description", null);
    assertNotNull(url);
    GIST_ID = url.substring(url.lastIndexOf('/') + 1);

    checkGistExists();
    checkGistNotAnonymous();
    checkGistPublic();
    checkGistDescription("description");
    checkGistContent(expected);
  }

  public void testEmpty() throws Throwable {
    List<NamedContent> expected = Collections.emptyList();

    String url = GithubCreateGistAction.createGist(myProject, myGitHubSettings.getAuthData(), expected, true, "description", null);
    assertNull("Gist was created", url);

    checkNotification(NotificationType.WARNING, "Can't create Gist", "Can't create empty gist");
  }

  public void testWrongLogin() throws Throwable {
    List<NamedContent> expected = createContent();

    GithubAuthData auth = myGitHubSettings.getAuthData();
    GithubAuthData myAuth = new GithubAuthData(auth.getHost(), auth.getLogin() + "some_suffix", auth.getPassword());
    String url = GithubCreateGistAction.createGist(myProject, myAuth, expected, true, "description", null);
    assertNull("Gist was created", url);

    checkNotification(NotificationType.ERROR, "Can't create Gist", null);
  }

  public void testWrongPassword() throws Throwable {
    List<NamedContent> expected = createContent();

    GithubAuthData auth = myGitHubSettings.getAuthData();
    GithubAuthData myAuth = new GithubAuthData(auth.getHost(), auth.getLogin(), auth.getPassword() + "some_suffix");
    String url = GithubCreateGistAction.createGist(myProject, myAuth, expected, true, "description", null);
    assertNull("Gist was created", url);

    checkNotification(NotificationType.ERROR, "Can't create Gist", null);
  }


}
