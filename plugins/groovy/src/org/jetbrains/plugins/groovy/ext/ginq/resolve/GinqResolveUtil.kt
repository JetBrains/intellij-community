// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.resolve

import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.ext.ginq.ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD
import org.jetbrains.plugins.groovy.ext.ginq.ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.types.GrSyntheticNamedRecordClass
import org.jetbrains.plugins.groovy.ext.ginq.types.inferDataSourceComponentType
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

fun resolveToCustomMember(place: PsiElement, name: String, tree: GinqExpression): GrLightVariable? {
  val facade = JavaPsiFacade.getInstance(place.project)
  if (name == "_g") {
    val containerClass = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, place.resolveScope) ?: return null
    val clazz = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD, place.resolveScope)
    val dataSourceTypes = tree.getDataSourceFragments().mapNotNull {
      val aliasName = it.alias.referenceName ?: return@mapNotNull null
      aliasName to lazyPub { inferDataSourceComponentType(it.dataSource.type) ?: PsiType.NULL }
    }.toMap()
    val type = clazz?.let { GrSyntheticNamedRecordClass(emptyList(), dataSourceTypes, emptyList(), it).type() } ?: return null
    val resultType = facade.elementFactory.createType(containerClass, type)
    return GrLightVariable(place.manager, "_g", resultType, containerClass)
  }
  if (name == "_rn") {
    val containerClass = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, place.resolveScope) ?: return null
    return GrLightVariable(place.manager, "_rn", CommonClassNames.JAVA_LANG_LONG, containerClass)
  }
  return null
}