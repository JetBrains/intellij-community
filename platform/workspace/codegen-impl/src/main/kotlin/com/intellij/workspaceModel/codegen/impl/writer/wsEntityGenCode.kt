// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.impl.writer.classes.implWsDataClassCode
import com.intellij.workspaceModel.codegen.impl.writer.classes.implWsEntityCode
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass

/**
 * TODO:
 * - Abstract Class do
 * - This about mutable lists (custom implementation?)
 *    - This about mutable lists for properties
 *    - This about mutable lists for references
 * - Facet: 2 parents, how to implement?
 * Поправить генерилку, чтоб все было чисто без лишних строк (почистил в applyToBuilder)
 * factory: ObjType<ChildSubSubEntity, *>
 * hasNewValue  -- Будем обсуждать с Сережей после НГ
 * setValue     -- Будем обсуждать с Сережей после НГ
 * builder(): ObjBuilder<*>
 * Привести билдер в порядок: поля идут первыми, потом applyBuilder, потом все утильные методы..
 *
 * Done:
 *   createReference     проверить нужны ли они в ентити (не нужны, грохнул)
 *   hasEqualProperties (забахали на всяк случай) // Там всего одно использование, посовещавшись мы решили хрохнуть его
 *   Поправить импорты
 *   Метод проверки, что все поля заиничены
 *
 * ---------------------------
 * Перенести тесты идеи к нам или наоборрот <--
 */

fun ObjClass<*>.implWsCode(): String? {
  if (!openness.instantiatable) return null
  return """
${implWsEntityCode()}
    
${implWsDataClassCode()}
    """.trimIndent()
}