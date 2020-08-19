// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast

import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtilBase
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.CollectClassMembersUtil
import java.util.*

class GeneratedConstructorCollector(tupleConstructor: PsiAnnotation?,
                                    immutable: Boolean,
                                    val builder: GrLightMethodBuilder) {

  val optional: Boolean = !immutable && tupleConstructor?.run { PsiUtil.getAnnoAttributeValue(this, "defaults", true) } ?: true

  val collector: MutableList<GrParameter> = mutableListOf()

  val includes: List<String>?
  val nameFilter: (String) -> Boolean

  init {
    val namesOrder: Pair<(String) -> Boolean, List<String>?>? = tupleConstructor?.let { collectNamesOrderInformation(it) }
    nameFilter = { it -> namesOrder?.first?.invoke(it) ?: true }
    includes = namesOrder?.second
  }


  fun accept(clazz: PsiClass, includeProperties: Boolean, includeBeans: Boolean, includeFields: Boolean) {
    val (properties, setters, fields) = getGroupedClassMembers(clazz)

    fun addParameter(origin: PsiField) {
      val name = origin.name
      if (!nameFilter(name) || origin.hasModifierProperty(PsiModifier.STATIC)) return
      val lightParameter: GrParameter = GrLightParameter(name, origin.type, builder).setOptional(optional)
      if (origin is GrField && optional) {
        lightParameter.initializerGroovy = origin.initializerGroovy
      }
      collector.add(lightParameter)
    }

    if (includeProperties) {
      for (property in properties) {
        addParameter(property)
      }
    }

    if (includeBeans) {
      for (method in setters) {
        val name = PropertyUtilBase.getPropertyNameBySetter(method)
        if (!nameFilter(name) || method.hasModifierProperty(PsiModifier.STATIC)) continue
        val type = PropertyUtilBase.getPropertyType(method) ?: error(method)
        collector.add(GrLightParameter(name, type, builder).setOptional(optional))
      }
    }

    if (includeFields) {
      for (field in fields) {
        if (field.hasModifierProperty(GrModifier.FINAL) && field.initializer != null) continue
        addParameter(field)
      }
    }
  }

  fun build(constructor: GrLightMethodBuilder) {
    if (includes != null) {
      val includeComparator = Comparator.comparingInt { param: GrParameter -> includes.indexOf(param.name) }
      collector.sortWith(includeComparator)
    }

    for (parameter in collector) {
      constructor.addParameter(parameter)
    }
  }

  companion object {

    private fun getGroupedClassMembers(psiClass: PsiClass): Triple<List<PsiField>, List<PsiMethod>, List<PsiField>> {
      val fields: MutableList<PsiField> = ArrayList()
      val properties: MutableList<PsiField> = ArrayList()
      val allFields = CollectClassMembersUtil.getFields(psiClass, false)
      for (field: PsiField in allFields) {
        if (field is GrField && field.isProperty) properties.add(field) else fields.add(field)
      }

      val methods: Array<PsiMethod> = CollectClassMembersUtil.getMethods(psiClass, false)
      val propertySetters = PropertyUtilBase.getAllProperties(true, false, methods)
      val fieldNames = allFields.map(PsiField::getName)
      val setters: List<PsiMethod> = propertySetters.filterKeys { !fieldNames.contains(it) }.values.toList()

      return Triple(properties, setters, fields)
    }

  }
}