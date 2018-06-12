// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport

abstract class NonFqnImport : GroovyImport {

  abstract val classFqn: String

  override fun resolveImport(file: GroovyFileBase): PsiClass? = file.resolve(this) {
    val facade = JavaPsiFacade.getInstance(file.project)
    val scope = file.resolveScope

    val clazz = facade.findClass(classFqn, scope)
    if (clazz != null) return@resolve clazz

    if (StringUtil.getShortName(classFqn) != classFqn) return@resolve null

    val fqn = StringUtil.getQualifiedName(file.packageName, classFqn)
    return@resolve facade.findClass(fqn, scope)
  }
}
