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
import com.intellij.jvm.createClass.CreateClassRequestImpl
import com.intellij.jvm.createClass.fix.CreateJvmClassFix
import com.intellij.jvm.createClass.ui.CreateClassUserInfo
import com.intellij.psi.SmartPointerManager
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import java.util.*

class CreateClassFromGroovyFix(ref: GrReferenceElement<*>) : CreateJvmClassFix<GrReferenceElement<*>>() {

  private val myRefPointer = SmartPointerManager.getInstance(ref.project).createSmartPsiElementPointer(ref)

  override val reference: GrReferenceElement<*>? get() = myRefPointer.element

  override fun getClassName(reference: GrReferenceElement<*>): String? = reference.referenceName

  override fun getJvmKinds(reference: GrReferenceElement<*>): Collection<JvmClassKind> {
    if (reference.parent is GrAnnotation) return EnumSet.of(JvmClassKind.ANNOTATION)
    return EnumSet.allOf(JvmClassKind::class.java)
  }

  override fun createRequest(reference: GrReferenceElement<*>, userInfo: CreateClassUserInfo): CreateClassRequestImpl {
    return CreateClassRequestImpl(
      reference,
      userInfo.targetDirectory,
      userInfo.classKind,
      userInfo.className
    )
  }
}