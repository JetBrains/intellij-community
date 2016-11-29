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
package git4idea.merge

abstract class GitMergeProviderTestBase : GitMergeProviderTestCase() {
  protected abstract fun `invoke conflicting operation`(branchCurrent: String, branchLast: String)

  fun `test merge - change vs change`() {
    `init branch - change`("A")
    `init branch - change`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions and paths loaded`("A", "B")
  }

  fun `test merge - change vs change and rename`() {
    `init branch - change`("A")
    `init branch - change and rename`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions and paths loaded`("A", "B")
  }

  fun `test merge - change and rename vs change`() {
    `init branch - change and rename`("A")
    `init branch - change`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions and paths loaded`("A", "B")
  }

  fun `test merge - change and rename vs change and rename - same renames`() {
    `init branch - change and rename`("A")
    `init branch - change and rename`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions and paths loaded`("A", "B")
  }

  fun `test change vs deleted`() {
    `init branch - change`("A")
    `init branch - delete`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions loaded`("A", "B")
    `assert revision GOOD, path GOOD`(Side.ORIGINAL)
    `assert revision GOOD, path BAD `(Side.LAST)
    `assert revision GOOD, path GOOD`(Side.CURRENT)
  }

  fun `test deleted vs change`() {
    `init branch - delete`("A")
    `init branch - change`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions loaded`("A", "B")
    `assert revision GOOD, path GOOD`(Side.ORIGINAL)
    `assert revision GOOD, path GOOD`(Side.LAST)
    `assert revision GOOD, path BAD `(Side.CURRENT)
  }

  fun `test rename vs deleted`() {
    `init branch - rename`("A")
    `init branch - delete`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions loaded`("A", "B")
    `assert revision GOOD, path BAD `(Side.ORIGINAL)
    `assert revision GOOD, path BAD `(Side.LAST)
    `assert revision GOOD, path GOOD`(Side.CURRENT)
  }

  fun `test deleted vs rename`() {
    `init branch - delete`("A")
    `init branch - rename`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions loaded`("A", "B")
    `assert revision GOOD, path BAD `(Side.ORIGINAL)
    `assert revision GOOD, path GOOD`(Side.LAST)
    `assert revision GOOD, path BAD `(Side.CURRENT)
  }
}
