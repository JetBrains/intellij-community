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
package org.jetbrains.plugins.groovy.util

/**
 * Splits fully qualified name into whatever goes before the [delimiter] (package name) and a short name.
 *
 * Examples:
 * - `"foo.bar.Baz" -> ("foo.bar", "Baz")`
 * - `"foo.bar.Outer.Inner" -> ("foo.bar.Outer", "Inner")`
 * - `"MyClassInDefaultPackage" -> ("", "MyClassInDefaultPackage")`
 */
fun getPackageAndShortName(fqn: String, delimiter: String = "."): Pair<String, String> {
  val index = fqn.lastIndexOf(delimiter)
  return if (index >= 0) fqn.substring(0, index) to fqn.substring(index + 1) else "" to fqn
}
