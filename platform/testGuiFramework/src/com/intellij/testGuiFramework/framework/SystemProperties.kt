// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework


/**
 * An annotation to provide custom system properties for a running instance of IDEA.
 * It assumed to have a next usage:
 * @code {
 *  @SystemProperties(arrayOf("key1=value1", "key2=value2"))
 *  SomeGuiTestClass
 * }
 */
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@kotlin.annotation.Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
annotation class SystemProperties(val keyValueArray: Array<String>)
