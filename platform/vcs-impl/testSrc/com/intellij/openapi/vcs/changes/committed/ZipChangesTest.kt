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
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import java.util.*

class ZipChangesTest : TestCase() {
  fun testTrivial() {
    test({
      !"A" - 0 - 1
    }, {
      !"A" - 0 - 1
    })

    test({
      !"A" - "B" - 0 - 1
    }, {
      !"A" - "B" - 0 - 1
    })

    test({
      -"A" - 0
    }, {
      -"A" - 0
    })

    test({
      +"A" - 0
    }, {
      +"A" - 0
    })

    ordered({
      !"A" - 0 - 1
      !"B" - 0 - 1
    }, {
      !"A" - 0 - 1
      !"B" - 0 - 1
    })
  }

  fun testSimple() {
    test({
      !"A" - 0 - 1
      !"A" - 1 - 2
    }, {
      !"A" - 0 - 2
    })

    test({
      -"X" - 0
      !"A" - 0 - 1
      !"A" - 1 - 2
      +"B" - 4
    }, {
      -"X" - 0
      !"A" - 0 - 2
      +"B" - 4
    })

    test({
      !"A" - 0 - 1
      !"A" - 1 - 2
      !"A" - 3 - 4
    }, {
      !"A" - 0 - 4
    })

    test({
      +"A" - 1
      !"A" - 1 - 2
    }, {
      +"A" - 2
    })

    test({
      !"A" - 0 - 1
      -"A" - 3
    }, {
      -"A" - 0
    })

    test({
      !"A" - 0 - 1
      !"X" - 0 - 1
      !"A" - 1 - 2
      !"X" - 3 - 4
    }, {
      !"A" - 0 - 2
      !"X" - 0 - 4
    })

    test({
      -"A" - 1
      +"B" - 1
      -"C" - 1
      +"D" - 1
    }, {
      -"A" - 1
      +"B" - 1
      -"C" - 1
      +"D" - 1
    })
  }

  fun testMovement() {
    test({
      !"A" - 0 - 1
      !"A" - "B" - 1 - 2
      !"B" - "B" - 2 - 3
    }, {
      !"A" - "B" - 0 - 3
    })

    test({
      +"A" - 0
      !"A" - 1 - 2
    }, {
      +"A" - 2
    })

    test({
      !"A" - "B" - 1 - 2
      -"B" - 3
    }, {
      -"A" - 1
    })

    test({
      !"A" - 0 - 1
      !"A" - "B" - 1 - 2
      !"B" - "C" - 2 - 3
    }, {
      !"A" - "C" - 0 - 3
    })

    test({
      !"A" - 0 - 1
      !"A" - "B" - 1 - 2
      !"A" - "A" - 3 - 4
    }, {
      !"A" - "B" - 0 - 2
      !"A" - "A" - 3 - 4
    })

    test({
      !"A" - "B" - 1 - 2
      !"B" - "C" - 3 - 4
      +"A" - 3
      !"A" - "X" - 5 - 6
    }, {
      !"A" - "C" - 1 - 4
      +"X" - 6
    })
  }

  fun testTricky() {
    /*
     * These might be non-optimal atm. Feel free to change the logic.
     */

    test({
      +"A" - 1
      -"A" - 2
    }, {
    })

    test({
      -"A" - 2
      +"A" - 4
    }, {
      -"A" - 2
      +"A" - 4
    })

    test({
      +"A" - 2
      +"A" - 4
    }, {
      +"A" - 2
      +"A" - 4
    })

    test({
      -"A" - 2
      -"A" - 4
    }, {
      -"A" - 2
      -"A" - 4
    })

    test({
      +"A" - 2
      !"A" - "B" - 3 - 4
      -"B" - 3
    }, {
    })

    test({
      -"A" - 2
      !"A" - "B" - 3 - 4
      -"B" - 3
    }, {
      -"A" - 2
      -"A" - 3
    })

    test({
      !"A" - "B" - 2 - 3
      !"B" - "A" - 2 - 3
    }, {
      !"A" - 2 - 3
    })

    test({
      !"A" - "B" - 2 - 3
      !"C" - "B" - 3 - 5
      !"B" - "X" - 3 - 5
    }, {
      !"A" - "X" - 2 - 5
      !"C" - "B" - 3 - 5
    })

    test({
      !"C" - "B" - 3 - 5
      !"A" - "B" - 2 - 3
      !"B" - "X" - 3 - 5
    }, {
      !"C" - "X" - 3 - 5
      !"A" - "B" - 2 - 3
    })

    test({
      -"A" - 2
      !"A" - "B" - 3 - 4
      +"B" - 5
    }, {
      -"A" - 2
      !"A" - "B" - 3 - 4
      +"B" - 5
    })
  }

  //
  // Impl
  //

  private fun ordered(before: ChangelistBuilder.() -> Unit, expected: ChangelistBuilder.() -> Unit) = test(before, expected, true)
  private fun test(before: ChangelistBuilder.() -> Unit, expected: ChangelistBuilder.() -> Unit, ordered: Boolean = false) {
    val clBefore = ChangelistBuilder()
    val clExpected = ChangelistBuilder()

    clBefore.before()
    clExpected.expected()

    val expectedChanges = clExpected.changes()
    val beforeChanges = clBefore.changes()

    val after = CommittedChangesTreeBrowser.zipChanges(beforeChanges)
    val afterChanges = after.map { MyChange(it) }

    UsefulTestCase.assertSameElements(afterChanges, expectedChanges)
    if (ordered) UsefulTestCase.assertOrderedEquals(afterChanges, expectedChanges)
  }

  private inner class ChangelistBuilder() {
    private val data = ArrayList<MyChange>()

    fun changes(): List<Change> = data

    operator fun String.not(): ModHelper = ModHelper(this, this, null, null)
    operator fun String.unaryMinus(): DelHelper = DelHelper(this, null)
    operator fun String.unaryPlus(): AddHelper = AddHelper(this, null)

    operator fun ModHelper.minus(path: String): ModHelper {
      aPath = path
      return this
    }

    operator fun ModHelper.minus(rev: Int): ModHelper {
      if (bRev == null) {
        bRev = rev
      }
      else {
        assertNull(aRev)
        aRev = rev
        data.add(change())
      }
      return this
    }

    operator fun DelHelper.minus(rev: Int) {
      bRev = rev
      data.add(change())
    }

    operator fun AddHelper.minus(rev: Int) {
      aRev = rev
      data.add(change())
    }

    inner class ModHelper(var bPath: String, var aPath: String, var bRev: Int?, var aRev: Int?) {
      fun change(): MyChange = MyChange(MyContentRevision(bPath, bRev), MyContentRevision(aPath, aRev))
    }

    inner class DelHelper(var bPath: String, var bRev: Int?) {
      fun change(): MyChange = MyChange(MyContentRevision(bPath, bRev), null)
    }

    inner class AddHelper(var aPath: String, var aRev: Int?) {
      fun change(): MyChange = MyChange(null, MyContentRevision(aPath, aRev))
    }
  }
}

private class MyChange(val before: MyContentRevision?, val after: MyContentRevision?) : Change(before, after) {
  constructor(change: Change) : this(change.beforeRevision as MyContentRevision?, change.afterRevision as MyContentRevision?)
  override fun toString(): String = "[$before, $after]"
  override fun hashCode(): Int = (before?.hashCode() ?: 0) + (after?.hashCode() ?: 0)
  override fun equals(other: Any?): Boolean {
    if (other !is MyChange) return false
    return before == other.before && after == other.after
  }
}

private class MyContentRevision(val path: String, val rev: Int?) : ContentRevision {
  override fun toString(): String = "$path - $rev"
  override fun getContent(): String? = null
  override fun getFile(): FilePath = LocalFilePath(path, false)
  override fun getRevisionNumber(): VcsRevisionNumber = MyRevisionNumber(rev)
  override fun hashCode(): Int = path.hashCode() + (rev?.hashCode() ?: 0)
  override fun equals(other: Any?): Boolean {
    if (other !is MyContentRevision) return false
    return path.equals(other.path) && rev == other.rev
  }
}

private class MyRevisionNumber(val rev: Int?) : VcsRevisionNumber {
  override fun compareTo(other: VcsRevisionNumber?): Int = 0
  override fun asString(): String = rev?.toString() ?: "null"
  override fun hashCode(): Int = rev ?: 0
  override fun equals(other: Any?): Boolean = other is MyRevisionNumber && other.rev == rev
}
