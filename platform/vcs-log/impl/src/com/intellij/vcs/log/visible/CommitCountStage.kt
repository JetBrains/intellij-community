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

enum class CommitCountStage(val count: Int) {
  INITIAL(5), FIRST_STEP(2000), ALL(Int.MAX_VALUE);

  fun next(): CommitCountStage {
    val values = values()
    return if (ordinal == values.size - 1) this else values[ordinal + 1]
  }
}

fun isAll(count: Int) = count < 0 || count == Int.MAX_VALUE
fun CommitCountStage.isAll() = isAll(count)