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

import com.intellij.openapi.util.Clock;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubGist;
import org.jetbrains.plugins.github.test.GithubTest;

import java.io.IOException;
import java.util.Map;

/**
 * @author Aleksey Pivovarov
 */
public abstract class GithubCreateGistTestBase extends GithubTest {
  protected String GIST_ID = null;
  protected GithubGist GIST = null;
  protected String GIST_DESCRIPTION;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    long time = Clock.getTime();
    GIST_DESCRIPTION = getTestName(false) + "_" + DateFormatUtil.formatDate(time);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      deleteGist();
    }
    finally {
      super.tearDown();
    }
  }

  protected void deleteGist() throws IOException {
    if (GIST_ID != null) {
      GithubApiUtil.deleteGist(myGitHubSettings.getAuthData(), GIST_ID);
      GIST = null;
      GIST_ID = null;
    }
  }

  @NotNull
  protected static Map<String, String> createContent() {
    Map<String, String> content = new HashMap<String, String>();

    content.put("file1", "file1 content");
    content.put("file2", "file2 content");
    content.put("dir_file3", "file3 content");

    return content;
  }

  @NotNull
  protected GithubGist getGist() {
    assertNotNull(GIST_ID);

    if (GIST == null) {
      try {
        GIST = GithubGist.create(GithubApiUtil.getGist(myGitHubSettings.getAuthData(), GIST_ID));
      }
      catch (IOException e) {
        System.err.println(e.getMessage());
      }
    }

    assertNotNull("Gist does not exist", GIST);
    return GIST;
  }

  protected void checkGistExists() {
    getGist();
  }

  protected void checkGistPublic() {
    GithubGist result = getGist();

    assertTrue("Gist does not public", result.isPublic());
  }

  protected void checkGistPrivate() {
    GithubGist result = getGist();

    assertFalse("Gist does not private", result.isPublic());
  }

  protected void checkGistAnonymous() {
    GithubGist result = getGist();

    assertTrue("Gist does not anonymous", result.getUser() == null);
  }

  protected void checkGistNotAnonymous() {
    GithubGist result = getGist();

    assertFalse("Gist does not anonymous", result.getUser() == null);
  }

  protected void checkGistDescription(@NotNull String expected) {
    GithubGist result = getGist();

    assertEquals("Gist content differs from sample", expected, result.getDescription());
  }

  protected void checkGistContent(@NotNull Map<String, String> expected) {
    GithubGist result = getGist();

    Map<String, String> files = result.getContent();


    assertEquals("Gist content differs from sample", expected, files);
  }

  protected void checkEquals(@NotNull Map<String, String> expected, @NotNull Map<String, String> actual) {
    assertEquals("Gist content differs from sample", expected, actual);
  }
}
