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
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.plugins.github.api.requests.GithubGistRequest.FileContent;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubAuthDataHolder;

import java.util.Collections;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreateGistTest extends GithubCreateGistTestBase {
  private final ProgressIndicator myIndicator = DumbProgressIndicator.INSTANCE;

  public void testSimple() {
    List<FileContent> expected = createContent();

    String url = GithubCreateGistAction.createGist(myProject, getAuthDataHolder(), myIndicator, expected, true, GIST_DESCRIPTION, null);
    assertNotNull(url);
    GIST_ID = url.substring(url.lastIndexOf('/') + 1);

    checkGistExists();
    checkGistNotAnonymous();
    checkGistPrivate();
    checkGistDescription(GIST_DESCRIPTION);
    checkGistContent(expected);
  }

  public void testAnonymous() {
    List<FileContent> expected = createContent();

    String url = GithubCreateGistAction
      .createGist(myProject, new GithubAuthDataHolder(GithubAuthData.createAnonymous(myHost)), myIndicator, expected, true,
                  GIST_DESCRIPTION, null);
    assertNotNull(url);
    GIST_ID = url.substring(url.lastIndexOf('/') + 1);

    checkGistExists();
    checkGistAnonymous();
    checkGistPrivate();
    checkGistDescription(GIST_DESCRIPTION);
    checkGistContent(expected);

    // anonymous gists - undeletable
    GIST_ID = null;
    GIST = null;
  }

  public void testUnusedFilenameField() {
    List<FileContent> expected = createContent();

    String url =
      GithubCreateGistAction.createGist(myProject, getAuthDataHolder(), myIndicator, expected, true, GIST_DESCRIPTION, "filename");
    assertNotNull(url);
    GIST_ID = url.substring(url.lastIndexOf('/') + 1);

    checkGistExists();
    checkGistNotAnonymous();
    checkGistPrivate();
    checkGistDescription(GIST_DESCRIPTION);
    checkGistContent(expected);
  }

  public void testUsedFilenameField() {
    List<FileContent> content = Collections.singletonList(new FileContent("file.txt", "file.txt content"));
    List<FileContent> expected = Collections.singletonList(new FileContent("filename", "file.txt content"));

    String url =
      GithubCreateGistAction.createGist(myProject, getAuthDataHolder(), myIndicator, content, true, GIST_DESCRIPTION, "filename");
    assertNotNull(url);
    GIST_ID = url.substring(url.lastIndexOf('/') + 1);

    checkGistExists();
    checkGistNotAnonymous();
    checkGistPrivate();
    checkGistDescription(GIST_DESCRIPTION);
    checkGistContent(expected);
  }

  public void testPublic() {
    List<FileContent> expected = createContent();

    String url = GithubCreateGistAction.createGist(myProject, getAuthDataHolder(), myIndicator, expected, false, GIST_DESCRIPTION, null);
    assertNotNull(url);
    GIST_ID = url.substring(url.lastIndexOf('/') + 1);

    checkGistExists();
    checkGistNotAnonymous();
    checkGistPublic();
    checkGistDescription(GIST_DESCRIPTION);
    checkGistContent(expected);
  }

  public void testEmpty() {
    List<FileContent> expected = Collections.emptyList();

    String url = GithubCreateGistAction.createGist(myProject, getAuthDataHolder(), myIndicator, expected, true, GIST_DESCRIPTION, null);
    assertNull("Gist was created", url);

    checkNotification(NotificationType.WARNING, "Can't create Gist", "Can't create empty gist");
  }
}
