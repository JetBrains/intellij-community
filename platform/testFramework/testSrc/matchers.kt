/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.testFramework

import org.hamcrest.Description
import org.hamcrest.Factory
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeDiagnosingMatcher
import java.io.File

public class FileExistenceMatcher(private val exists: Boolean) : TypeSafeDiagnosingMatcher<File>(javaClass<File>()) {
  override fun matchesSafely(file: File, mismatchDescription: Description): Boolean {
    if (exists == file.exists()) {
      return true
    }

    mismatchDescription.appendText("is not a file")
    return false
  }

  override fun describeTo(description: Description) {
    description.appendText("an existing file")
  }
}

public Factory fun exists(): Matcher<File> = FileExistenceMatcher(true)