/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.log;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsLogManager;
import git4idea.GitVcs;
import git4idea.test.GitSingleRepoTest;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GitLogProviderTest extends GitSingleRepoTest {

  @NotNull private GitLogProvider myLogProvider;

  public void setUp() throws Exception {
    super.setUp();
    List<VcsLogProvider> providers =
      ContainerUtil.filter(Extensions.getExtensions(VcsLogManager.LOG_PROVIDER_EP, myProject), new Condition<VcsLogProvider>() {
        @Override
        public boolean value(VcsLogProvider provider) {
          return provider.getSupportedVcs().equals(GitVcs.getKey());
        }
      });
    assertEquals("Incorrect number of GitLogProviders", 1, providers.size());
    myLogProvider = (GitLogProvider)providers.get(0);
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testGetCurrentUser() throws Exception {
    VcsUser user = myLogProvider.getCurrentUser(myProjectRoot);
    assertNotNull("User is not defined", user);
    assertEquals("User name is incorrect", GitTestUtil.USER_NAME, user.getName());
    assertEquals("User email is incorrect", GitTestUtil.USER_EMAIL, user.getEmail());
  }

  public void testGetContainingBranches() throws Exception {

  }
}
