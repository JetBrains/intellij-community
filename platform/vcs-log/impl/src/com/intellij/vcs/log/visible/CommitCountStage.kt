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
package com.intellij.vcs.log.visible

open class CommitCountStage(val count: Int) {
  open fun next(): CommitCountStage = ALL
  override fun toString(): String = if (isAll()) "ALL" else count.toString()

  companion object {
    @JvmField val ALL = CommitCountStage(Int.MAX_VALUE)
    @JvmField val FIRST_STEP = CommitCountStage(2000)
    @JvmField val INITIAL = object : CommitCountStage(5) {
      override fun next() = FIRST_STEP
    }
  }
}

fun isAll(count: Int) = count < 0 || count == Int.MAX_VALUE
fun CommitCountStage.isAll() = isAll(count)