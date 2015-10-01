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
package com.intellij.openapi.util.io

import java.io.File

val File.systemIndependentPath: String
  get() = path.replace(File.separatorChar, '/')

val File.parentSystemIndependentPath: String
  get() = getParent().replace(File.separatorChar, '/')

val String.parentPath: String?
  get() {
    if (isEmpty()) {
      return null
    }
    var end = Math.max(lastIndexOf('/'), lastIndexOf('\\'))
    if (end == length() - 1) {
      end = Math.max(lastIndexOf('/', end - 1), lastIndexOf('\\', end - 1))
    }
    return if (end == -1) null else substring(0, end)
  }