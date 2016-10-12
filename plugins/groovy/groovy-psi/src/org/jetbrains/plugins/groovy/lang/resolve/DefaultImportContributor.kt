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

private val classes = listOf(
    Import("java.math.BigInteger"),
    Import("java.math.BigDecimal")
)

private val packages = listOf(
    Import("java.lang", ImportType.STAR),
    Import("java.util", ImportType.STAR),
    Import("java.io", ImportType.STAR),
    Import("java.net", ImportType.STAR),
    Import("groovy.lang", ImportType.STAR),
    Import("groovy.util", ImportType.STAR)
)

private val imports = classes + packages

class DefaultImportContributor : GrImportContributor {

  override fun getImports(file: GroovyFile): Collection<Import> = imports
}