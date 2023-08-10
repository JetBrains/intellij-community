/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof

import com.intellij.openapi.util.Disposer
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.jvm.isAccessible

class ObjectLayoutAssumptionTest {

  @Test
  fun disposerAssumptions() {
    // Disposer class is in correct package
    val disposerClass = Disposer::class
    assertEquals("com.intellij.openapi.util.Disposer", disposerClass.qualifiedName)

    // Disposer.ourTree validation
    val ourTreeClass =
      disposerClass.members.first { c -> c.name == "ourTree" }.returnType.classifier as KClass<*>
    assertEquals("com.intellij.openapi.util.ObjectTree",
                 ourTreeClass.qualifiedName)
  }

  @Test
  fun objectTreeAssumptions() {
    val disposerClass = Disposer::class
    val objectTreeInstance =
      disposerClass.members.first { c -> c.name == "ourTree" }.apply { isAccessible = true }.call()!!
    val myObject2NodeMapField = objectTreeInstance.javaClass.getDeclaredField("myObject2ParentNode").apply { isAccessible = true }
    val map = myObject2NodeMapField.get(objectTreeInstance)

    // ObjectTree.myObject2ParentNode validation
    assertEquals("it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap", map.javaClass.canonicalName)
  }
}
