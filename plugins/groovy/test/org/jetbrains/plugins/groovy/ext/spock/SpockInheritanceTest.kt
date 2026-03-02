// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors.LIB_GROOVY_4_0
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.RepositoryTestLibrary
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

internal class SpockInheritanceTest: LightGroovyTestCase() {
  private val descriptor = LibraryLightProjectDescriptor(
    LIB_GROOVY_4_0.plus(RepositoryTestLibrary("org.spockframework:spock-core:2.4-groovy-4.0"))
  )

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return descriptor
  }

  fun testNoRecursionWithNestedType() {
    val file = myFixture.configureByText(
        GroovyFileType.GROOVY_FILE_TYPE, """
                  package b
                  
                  import spock.lang.Specification
                  import static b.SomeClass.ServerEndpoint.A
                  
                  abstract class Some<caret>Class<T> extends SomeClassBase<T> {
                    enum ServerEndpoint {
                      A
                    }
                  }
                  
                  abstract class SomeClassBase<T> extends Specificatio { // typo is intentional
                  
                  }
    """.trimIndent())
    val identifier = file.findElementAt(editor.caretModel.offset)
    checkNotNull(identifier)
    val clazz = identifier.parentOfType<GrTypeDefinition>()
    checkNotNull(clazz)
    assertFalse(InheritanceUtil.isInheritor(clazz, "spock.lang.Specification"))
  }

}