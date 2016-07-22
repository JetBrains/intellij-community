/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.passwordSafe

import com.intellij.ide.passwordSafe.masterKey.FilePasswordSafeProvider
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

class FilePasswordSafeProviderTest {
  private val tempDirManager = TemporaryDirectory()

    @Rule
    @JvmField
    val ruleChain = RuleChain(tempDirManager)

  @Test
  fun test() {
    val baseDir = tempDirManager.newPath()
    val provider = FilePasswordSafeProvider(baseDirectory = baseDir)

    assertThat(baseDir).doesNotExist()
    assertThat(provider.getPassword("foo")).isNull()

    provider.setPassword("foo", "pass")

    assertThat(baseDir).doesNotExist()

    val pdbFile = baseDir.resolve("pdb")
    val pdbPwdFile = baseDir.resolve("pdb.pwd")
    val pdbPwdTmpFile = baseDir.resolve("pdb.pwd.tmp")

    provider.save()

    assertThat(pdbFile).isRegularFile()
    assertThat(pdbPwdFile).isRegularFile()
    assertThat(pdbPwdTmpFile).doesNotExist()
  }
}
