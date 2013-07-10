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

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.test.GithubTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.github.GithubCreateGistAction.NamedContent;

/**
 * @author Aleksey Pivovarov
 */
public abstract class GithubCreateGistTestBase extends GithubTest {
  protected String GIST_ID = null;
  protected JsonObject GIST = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
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
      GithubUtil.deleteGist(myGitHubSettings.getAuthData(), GIST_ID);
      GIST = null;
      GIST_ID = null;
    }
  }

  @NotNull
  protected static List<NamedContent> createContent() {
    List<NamedContent> content = new ArrayList<NamedContent>();

    content.add(new NamedContent("file1", "file1 content"));
    content.add(new NamedContent("file2", "file2 content"));
    content.add(new NamedContent("dir_file3", "file3 content"));

    return content;
  }

  @NotNull
  protected JsonObject getGist() {
    assertNotNull(GIST_ID);

    if (GIST == null) {
      try {
        GIST = GithubUtil.getGist(myGitHubSettings.getAuthData(), GIST_ID);
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
    JsonObject result = getGist();

    assertTrue("Gist does not public", result.get("public").getAsBoolean());
  }

  protected void checkGistPrivate() {
    JsonObject result = getGist();

    assertFalse("Gist does not private", result.get("public").getAsBoolean());
  }

  protected void checkGistAnonymous() {
    JsonObject result = getGist();

    assertTrue("Gist does not anonymous", result.get("user").isJsonNull());
  }

  protected void checkGistNotAnonymous() {
    JsonObject result = getGist();

    assertFalse("Gist does not anonymous", result.get("user").isJsonNull());
  }

  protected void checkGistDescription(@NotNull String expected) {
    JsonObject result = getGist();

    assertEquals("Gist content differs from sample", expected, result.get("description").getAsString());
  }

  protected void checkGistContent(@NotNull List<NamedContent> expected) {
    JsonObject result = getGist();

    JsonObject files = result.get("files").getAsJsonObject();

    for (NamedContent file : expected) {
      String content = files.get(file.getName()).getAsJsonObject().get("content").getAsString();
      assertEquals("Gist content differs from sample", file.getText(), content);
    }
  }

  protected void checkEquals(@NotNull List<NamedContent> expected, @NotNull List<NamedContent> actual) {
    for (NamedContent file : expected) {
      assertTrue("Not found: <" + file.getName() + "> " + file.getText(), actual.contains(file));
    }

    assertEquals("Expected less elements", expected.size(), actual.size());
  }
}
