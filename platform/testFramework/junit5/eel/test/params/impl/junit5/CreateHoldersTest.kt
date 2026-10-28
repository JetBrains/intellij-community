// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.junit5

import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.testFramework.junit5.eel.params.api.WslTest
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider.StartResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.AnnotatedElement

/**
 * Annotations on the test method and on the test class give holders together, and equal annotations give one holder.
 */
internal class CreateHoldersTest {
  @Test
  fun testMethodAndClassAnnotationsAreMerged() {
    assertEquals(listOf("method" to true, "class" to true), holders<AnnotatedClass>("differentDistros"))
  }

  @Test
  fun testEqualAnnotationGivesOneHolder() {
    assertEquals(listOf("class" to true), holders<AnnotatedClass>("sameDistro"))
  }

  @Test
  fun testAnnotationWithOtherArgumentGivesTwoHolders() {
    assertEquals(listOf("class" to false, "class" to true), holders<AnnotatedClass>("sameDistroNotMandatory"))
  }

  @Test
  fun testNoAnnotationGivesHolderWithoutAnnotation() {
    assertEquals(listOf(null), createHolders(FakeWslProvider, elements<NotAnnotatedClass>("noAnnotation")).map { it.annotation })
  }

  private inline fun <reified C : Any> holders(methodName: String): List<Pair<String, Boolean>> =
    createHolders(FakeWslProvider, elements<C>(methodName)).map {
      val annotation = it.annotation!!
      annotation.distroNamePart to annotation.mandatory
    }

  private inline fun <reified C : Any> elements(methodName: String): List<AnnotatedElement> =
    listOf(C::class.java.getDeclaredMethod(methodName), C::class.java)
}

@WslTest("class")
private class AnnotatedClass {
  @WslTest("method")
  fun differentDistros(): Unit = Unit

  @WslTest("class")
  fun sameDistro(): Unit = Unit

  @WslTest("class", mandatory = false)
  fun sameDistroNotMandatory(): Unit = Unit
}

private class NotAnnotatedClass {
  fun noAnnotation(): Unit = Unit
}

private object FakeWslProvider : EelIjentTestProvider<WslTest> {
  override val name: String = "fake WSL"
  override val mandatoryAnnotationClass = WslTest::class

  override suspend fun start(scope: ParentOfIjentScopes, annotation: WslTest?): StartResult =
    error("The test must not start an environment")

  override fun annotationToUserVisibleString(annotation: WslTest): String = annotation.distroNamePart
  override fun isMandatory(annotation: WslTest): Boolean = annotation.mandatory
}
