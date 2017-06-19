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

import com.intellij.jvm.createClass.api.JvmClassKind
import com.intellij.jvm.createClass.api.KeyedClassKind
import com.intellij.lang.Language
import icons.JetgroovyIcons
import org.jetbrains.plugins.groovy.GroovyLanguage
import javax.swing.Icon

enum class GroovyClassKind(
  override val icon: Icon?,
  override val displayName: String,
  override val key: Any?
) : KeyedClassKind {

  CLASS(JetgroovyIcons.Groovy.Class, "Class", JvmClassKind.CLASS),
  INTERFACE(JetgroovyIcons.Groovy.Interface, "Interface", JvmClassKind.INTERFACE),
  ANNOTATION(JetgroovyIcons.Groovy.AnnotationType, "Annotation", JvmClassKind.ANNOTATION),
  ENUM(JetgroovyIcons.Groovy.Enum, "Enum", JvmClassKind.ENUM),
  TRAIT(JetgroovyIcons.Groovy.Trait, "Trait", null);

  override val language: Language get() = GroovyLanguage

}