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
package org.assertj.core.api

import org.assertj.core.internal.ComparatorBasedComparisonStrategy
import org.assertj.core.internal.Iterables
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

fun AbstractPathAssert<*>.hasChildren(vararg names: String) {
  paths.assertIsDirectory(info, actual)

  Iterables(ComparatorBasedComparisonStrategy(object : Comparator<Any> {
    override fun compare(o1: Any, o2: Any): Int {
      if (o1 is Path && o2 is Path) {
        return o1.compareTo(o2)
      }
      else if (o1 is String && o2 is String) {
        return o1.compareTo(o2)
      }
      else {
        return if ((o1 as Path).endsWith(o2 as String)) 0 else -1
      }
    }
  }))
    .assertContainsOnly(info, Files.newDirectoryStream(actual).toList(), names)
}

