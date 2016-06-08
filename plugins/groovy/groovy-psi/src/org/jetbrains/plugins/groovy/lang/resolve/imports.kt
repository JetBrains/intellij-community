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
package org.jetbrains.plugins.groovy.lang.resolve

import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

/**
 * regular: class
 * static: class member
 * star: classes in package
 * static star: members of class
 */
class Import(
    val name: String,
    val static: Boolean = false,
    val star: Boolean = false
)

abstract class GrImportContributorBase : GrImportContributor {

  abstract fun appendImplicitlyImportedPackages(file: GroovyFile): List<String>

  final override fun getImports(file: GroovyFile): Collection<Import> {
    return appendImplicitlyImportedPackages(file).map {
      Import(name = it, star = true)
    }
  }
}