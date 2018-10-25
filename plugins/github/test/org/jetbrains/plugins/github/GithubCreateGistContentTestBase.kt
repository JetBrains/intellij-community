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

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.requests.GithubGistRequest.FileContent;
import org.jetbrains.plugins.github.test.GithubTest;

import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public abstract class GithubCreateGistContentTestBase extends GithubTest {

  @Override
  protected void beforeTest() {
    createProjectFiles();
  }

  protected void checkEquals(@NotNull List<FileContent> expected, @NotNull List<FileContent> actual) {
    assertTrue("Gist content differs from sample", Comparing.haveEqualElements(expected, actual));
  }
}
