// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.junit.jupiter.api.Test

@TestApplication
internal class KotlinClassDiagnosticRangeTest : LightJavaCodeInsightFixtureTestCase5(
    ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()
) {
    @Test
    fun missingJavaInterfaceMember() = checkHighlighting(
        """
        /** The writer. */
        <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">public class Writer : AutoCloseable</error> // The body follows.
        {
            fun other() {}
        }
        """
    )

    @Test
    fun missingAbstractClassMember() = checkHighlighting(
        """
        abstract class Base { abstract fun close() }

        <error descr="[ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED]">class Writer : Base()</error> {}
        """
    )

    @Test
    fun declarationWithoutBody() = checkHighlighting(
        """
        interface Contract { fun close() }

        /** The writer. */
        <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">public class Writer : Contract</error>
        """
    )

    @Test
    fun multilineHeader() = checkHighlighting(
        """
        interface Contract { fun close() }

        <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">class Writer<T>(val value: T) :
            Contract where T : CharSequence</error> {
        }
        """
    )

    @Test
    fun objects() = checkHighlighting(
        """
        interface Contract { fun close() }

        <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">object Writer : Contract</error> {}
        val writer = <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">object : Contract</error> {}
        """
    )

    @Test
    fun conflictingInterfaceMembers() = checkHighlighting(
        """
        interface First { fun close() {} }
        interface Second { fun close() {} }

        <error descr="[MANY_INTERFACES_MEMBER_NOT_IMPLEMENTED]">class Writer : First, Second</error> {}
        """
    )

    @Test
    fun conflictingClassAndInterfaceMembers() = checkHighlighting(
        """
        open class Base { open fun close() {} }
        interface Contract { fun close() {} }

        <error descr="[MANY_IMPL_MEMBER_NOT_IMPLEMENTED]">class Writer : Base(), Contract</error> {}
        """
    )

    @Test
    fun unresolvedSupertype() = checkHighlighting(
        """
        class Writer : <error descr="[UNRESOLVED_REFERENCE]">Missing</error> {}
        """
    )

    @Test
    fun classRedeclaration() = checkHighlighting(
        """
        open class Base
        class <error descr="[CLASSIFIER_REDECLARATION]">Writer</error> : Base()
        class <error descr="[CLASSIFIER_REDECLARATION]">Writer</error> : Base()
        """
    )

    private fun checkHighlighting(text: String): Unit = timeoutRunBlocking {
        withContext(Dispatchers.EDT) {
            fixture.configureByText("Writer.kt", text.trimIndent())
            fixture.checkHighlighting(false, false, false)
        }
    }
}
