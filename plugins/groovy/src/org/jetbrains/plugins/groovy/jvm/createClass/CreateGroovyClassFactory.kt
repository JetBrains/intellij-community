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
package org.jetbrains.plugins.groovy.jvm.createClass

import com.intellij.jvm.JvmClassKind
import com.intellij.jvm.createClass.CreateClassRequest
import com.intellij.jvm.createClass.CreateJvmClassFactory
import com.intellij.jvm.createClass.SourceClassKind
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

class CreateGroovyClassFactory : CreateJvmClassFactory {

  override fun getSourceKinds(jvmClassKinds: Collection<JvmClassKind>, context: PsiElement): Collection<SourceClassKind> {
    return jvmClassKinds.flatMap {
      when (it) {
        JvmClassKind.CLASS -> listOf(GroovyClassKind.CLASS)
        JvmClassKind.INTERFACE -> listOf(GroovyClassKind.INTERFACE, GroovyClassKind.TRAIT)
        JvmClassKind.ANNOTATION -> listOf(GroovyClassKind.ANNOTATION)
        JvmClassKind.ENUM -> listOf(GroovyClassKind.ENUM)
      }
    }
  }

  override fun createClass(request: CreateClassRequest): PsiClass {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}
