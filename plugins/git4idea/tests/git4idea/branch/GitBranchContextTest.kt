/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.branch

import com.intellij.tasks.context.WorkingContextManager
import git4idea.test.GitPlatformTest
import junit.framework.TestCase

class GitBranchContextTest: GitPlatformTest() {

  fun testContextManager() {
    val contextManager = WorkingContextManager.getInstance(project)
    TestCase.assertNotNull(contextManager)
  }
}