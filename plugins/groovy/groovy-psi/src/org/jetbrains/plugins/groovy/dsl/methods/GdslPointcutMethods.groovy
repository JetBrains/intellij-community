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
package org.jetbrains.plugins.groovy.dsl.methods

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.dsl.DslPointcut
import org.jetbrains.plugins.groovy.dsl.GdslMethod
import org.jetbrains.plugins.groovy.dsl.GdslType
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor

@CompileStatic
@SuppressWarnings("GroovyUnusedDeclaration")
trait GdslPointcutMethods {

  def bind(final Object arg) {
    DslPointcut.bind(arg)
  }

  def handleImplicitBind(arg) {
    if (arg instanceof Map && arg.size() == 1 &&
        arg.keySet().iterator().next() instanceof String &&
        arg.values().iterator().next() instanceof DslPointcut) {
      return DslPointcut.bind(arg)
    }
    return arg
  }

  DslPointcut<GdslType, GdslType> subType(final Object arg) {
    DslPointcut.subType(handleImplicitBind(arg))
  }

  DslPointcut<GroovyClassDescriptor, GdslType> currentType(final Object arg) {
    DslPointcut.currentType(handleImplicitBind(arg))
  }

  DslPointcut<GroovyClassDescriptor, GdslType> enclosingType(final Object arg) {
    DslPointcut.enclosingType(handleImplicitBind(arg))
  }

  DslPointcut<Object, String> name(final Object arg) {
    DslPointcut.name(handleImplicitBind(arg))
  }

  DslPointcut<GroovyClassDescriptor, GdslMethod> enclosingMethod(final Object arg) {
    DslPointcut.enclosingMethod(handleImplicitBind(arg))
  }
}
