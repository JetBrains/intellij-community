// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.annotations

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrClassDefinitionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeParameterImpl
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GrTypeParameterAnnotationsTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0)
  }

  private val myFixture by lightGroovyFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.addFileToProject("Anno1.groovy", """
    
    import java.lang.annotation.ElementType
    import java.lang.annotation.Target
    
    @Target([ElementType.TYPE_USE])
    public @interface Anno1 {
    }
    """.trimIndent())

    myFixture.addFileToProject("Anno2.groovy", """
    
    import java.lang.annotation.ElementType
    import java.lang.annotation.Target
    
    @Target([ElementType.TYPE_USE])
    public @interface Anno2 {
    
    }
    """.trimIndent())
  }

  @Test
  fun testGetAnnotationsPsi() {
    val file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
      class A<@Anno1 @Anno2 <caret> T> {}
    """.trimIndent())

    runReadActionBlocking {
      val element = file.findElementAt(myFixture.caretOffset)
      val typeParameter = PsiTreeUtil.getParentOfType(element, GrTypeParameter::class.java)

      assertNotNull(typeParameter)
      val annotations = typeParameter!!.annotations
      assertEquals(2, annotations.size)
      assertEquals("Anno1", annotations[0].qualifiedName)
      assertEquals("Anno2", annotations[1].qualifiedName)
    }
  }

  @Test
  fun testFindAnnotationPsi() {
    val file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
      class A<@Anno1 @Anno2 <caret> T> {}
    """.trimIndent())

    runReadActionBlocking {
      val element = file.findElementAt(myFixture.caretOffset)
      val typeParameter = PsiTreeUtil.getParentOfType(element, GrTypeParameter::class.java)

      assertNotNull(typeParameter)
      val firstAnnotation =  typeParameter!!.findAnnotation("Anno1")
      assertNotNull(firstAnnotation)
      val secondAnnotation = typeParameter.findAnnotation("Anno2")
      assertNotNull(secondAnnotation)
    }
  }

  @Test
  fun testGetAnnotationsStub() {
    val file = myFixture.addFileToProject("a.groovy", """
      class A<@Anno1 @Anno2 T> {}
    """.trimIndent())

    runReadActionBlocking {
      val stub = (file as PsiFileImpl).stub
      assertNotNull(stub)
      val childrenStubList = stub!!.childrenStubs
      assertEquals(1, childrenStubList.size)

      val classDef = assertInstanceOf(GrClassDefinitionImpl::class.java, childrenStubList.first().psi)
      assertNotNull(classDef.stub)
      val typeParameterList = classDef.typeParameters
      assertNotNull(typeParameterList)
      assertEquals(1, typeParameterList.size)

      val typeParameter = assertInstanceOf(GrTypeParameterImpl::class.java, typeParameterList.first())
      assertNotNull(typeParameter.stub)

      val annotations = typeParameter.annotations
      assertEquals(2, annotations.size)

      annotations.forEachIndexed { idx, anno ->
        val castedAnnotation = assertInstanceOf(GrAnnotationImpl::class.java, anno)
        assertNotNull(castedAnnotation.stub)
        assertEquals("Anno${idx + 1}", castedAnnotation.qualifiedName)
      }
    }
  }

  @Test
  fun testFindAnnotationStub() {
    val file = myFixture.addFileToProject("a.groovy", """
      class A<@Anno1 T> {}
    """.trimIndent())

    runReadActionBlocking {
      val stub = (file as PsiFileImpl).stub
      assertNotNull(stub)
      val childrenStubList = stub!!.childrenStubs
      assertEquals(1, childrenStubList.size)

      val classDef = assertInstanceOf(GrClassDefinitionImpl::class.java, childrenStubList.first().psi)
      assertNotNull(classDef.stub)
      val typeParameterList = classDef.typeParameters
      assertNotNull(typeParameterList)
      assertEquals(1, typeParameterList.size)

      val typeParameter = assertInstanceOf(GrTypeParameterImpl::class.java, typeParameterList.first())
      assertNotNull(typeParameter.stub)

      val candidate = typeParameter.findAnnotation("Anno1")
      val annotation = assertInstanceOf(GrAnnotationImpl::class.java, candidate)
      assertNotNull(annotation.stub)
      assertEquals("Anno1", annotation.qualifiedName)
    }
  }
}