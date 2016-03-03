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
package com.intellij.openapi.util.io

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtilRt
import java.io.File

val File.systemIndependentPath: String
  get() = path.replace(File.separatorChar, '/')

val File.parentSystemIndependentPath: String
  get() = parent.replace(File.separatorChar, '/')

// PathUtilRt.getParentPath returns empty string if no parent path, but in Kotlin "null" is better because elvis operator could be used
fun getParentPath(path: String) = StringUtil.nullize(PathUtilRt.getParentPath(path))

fun endsWithSlash(path: String) = path.getOrNull(path.length - 1) == '/'

fun endsWithName(path: String, name: String) = path.endsWith(name) && (path.length == name.length || path.getOrNull(path.length - name.length - 1) == '/')