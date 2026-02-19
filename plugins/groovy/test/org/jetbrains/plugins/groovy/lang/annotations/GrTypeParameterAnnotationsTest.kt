// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.annotations

import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrClassDefinitionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeParameterImpl


class GrTypeParameterAnnotationsTest : LightGroovyTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = GroovyProjectDescriptors.GROOVY_4_0

  override fun setUp() {
    super.setUp()
    fixture.addFileToProject("Anno1.groovy", """
    
    import java.lang.annotation.ElementType
    import java.lang.annotation.Target
    
    @Target([ElementType.TYPE_USE])
    public @interface Anno1 {
    }
    """.trimIndent())


    fixture.addFileToProject("Anno2.groovy", """
    
    import java.lang.annotation.ElementType
    import java.lang.annotation.Target
    
    @Target([ElementType.TYPE_USE])
    public @interface Anno2 {
    
    }
    """.trimIndent())
  }

  fun testGetAnnotationsPsi() {
    val file = fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
      class A<@Anno1 @Anno2 <caret> T> {}
    """.trimIndent())

    val element = file.findElementAt(fixture.caretOffset)
    val typeParameter = PsiTreeUtil.getParentOfType(element, GrTypeParameter::class.java)

    assertNotNull(typeParameter)
    val annotations = typeParameter!!.annotations
    assertEquals(2, annotations.size)
    assertEquals("Anno1", annotations[0].qualifiedName)
    assertEquals("Anno2", annotations[1].qualifiedName)
  }


  fun testFindAnnotationPsi() {
    val file = fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
      class A<@Anno1 @Anno2 <caret> T> {}
    """.trimIndent())

    val element = file.findElementAt(fixture.caretOffset)
    val typeParameter = PsiTreeUtil.getParentOfType(element, GrTypeParameter::class.java)

    assertNotNull(typeParameter)
    val firstAnnotation =  typeParameter!!.findAnnotation("Anno1")
    assertNotNull(firstAnnotation)
    val secondAnnotation = typeParameter.findAnnotation("Anno2")
    assertNotNull(secondAnnotation)
  }

  fun testGetAnnotationsStub() {
    val file = fixture.addFileToProject("a.groovy", """
      class A<@Anno1 @Anno2 T> {}
    """.trimIndent())

    val stub = (file as PsiFileImpl).stub
    assertNotNull(stub)
    val childrenStubList = stub!!.childrenStubs
    assertSize(1, childrenStubList)

    val classDef = assertInstanceOf(childrenStubList.first().psi, GrClassDefinitionImpl::class.java)
    assertNotNull(classDef.stub)
    val typeParameterList = classDef.typeParameters
    assertNotNull(typeParameterList)
    assertSize(1, typeParameterList)

    val typeParameter = assertInstanceOf(typeParameterList.first(), GrTypeParameterImpl::class.java)
    assertNotNull(typeParameter.stub)

    val annotations = typeParameter.annotations
    assertEquals(2, annotations.size)

    annotations.forEachIndexed { idx, anno ->
      val castedAnnotation = assertInstanceOf(anno, GrAnnotationImpl::class.java)
      assertNotNull(castedAnnotation.stub)
      assertEquals("Anno${idx + 1}", castedAnnotation.qualifiedName)
    }
  }

  fun testFindAnnotationStub() {
    val file = fixture.addFileToProject("a.groovy", """
      class A<@Anno1 T> {}
    """.trimIndent())

    val stub = (file as PsiFileImpl).stub
    assertNotNull(stub)
    val childrenStubList = stub!!.childrenStubs
    assertSize(1, childrenStubList)

    val classDef = assertInstanceOf(childrenStubList.first().psi, GrClassDefinitionImpl::class.java)
    assertNotNull(classDef.stub)
    val typeParameterList = classDef.typeParameters
    assertNotNull(typeParameterList)
    assertSize(1, typeParameterList)

    val typeParameter = assertInstanceOf(typeParameterList.first(), GrTypeParameterImpl::class.java)
    assertNotNull(typeParameter.stub)


    val candidate = typeParameter.findAnnotation("Anno1")
    val annotation = assertInstanceOf(candidate, GrAnnotationImpl::class.java)
    assertNotNull(annotation.stub)
    assertEquals("Anno1", annotation.qualifiedName)
  }
}