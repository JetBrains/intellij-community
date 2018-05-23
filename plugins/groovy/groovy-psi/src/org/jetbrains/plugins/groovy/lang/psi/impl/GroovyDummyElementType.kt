// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilderFactory
import com.intellij.psi.tree.ILazyParseableElementType
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser

object GroovyDummyElementType : ILazyParseableElementType("GROOVY_DUMMY_ELEMENT", GroovyLanguage) {

  override fun parseContents(chameleon: ASTNode): ASTNode {
    val dummyElement = chameleon as GroovyDummyElement
    val builder = PsiBuilderFactory.getInstance().createBuilder(chameleon.psi.project, chameleon)
    GroovyParser().parseLight(dummyElement.childType, builder)
    return builder.treeBuilt
  }
}
