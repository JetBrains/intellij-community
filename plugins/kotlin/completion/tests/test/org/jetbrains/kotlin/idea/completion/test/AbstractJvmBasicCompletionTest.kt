// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinJdkAndLibraryProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

abstract class AbstractJvmBasicCompletionTest : KotlinFixtureCompletionBaseTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = object : KotlinJdkAndLibraryProjectDescriptor(
        libraryFiles = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE.libraryFiles,
        librarySourceFiles = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE.librarySourceFiles,
    ) {
        override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk16()
    }

    override fun getPlatform() = JvmPlatforms.jvm16
    override fun defaultCompletionType() = CompletionType.BASIC

    override fun configureFixture(testPath: String) {
        // Kotlin SDK references many JDK classes via typealiases.
        // Some of them are missing in the mockJDKs of intellij repository,
        // so we have to add them to the fixture by hand
        addCharacterCodingException()
        addAppendable()
        addHashSet()
        addLinkedHashSet()

        super.configureFixture(testPath)
    }

    private fun addCharacterCodingException() {
        myFixture.addClass(
            """
            package java.nio.charset;
                                    
            import java.io.IOException;            
            
            public class CharacterCodingException extends IOException {}
            """.trimIndent()
        )
    }

    private fun addAppendable() {
        myFixture.addClass(
            """
            package java.lang;
            
            import java.io.IOException;
            
            public interface Appendable {
                Appendable append(CharSequence csq) throws IOException;
                Appendable append(CharSequence csq, int start, int end) throws IOException;
                Appendable append(char c) throws IOException;
            }
            """.trimIndent()
        )
    }

    private fun addHashSet() {
        myFixture.addClass(
            """
            package java.util;
            
            import java.io.Serializable;
            
            public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, Serializable {
                @Override
                public Iterator<E> iterator() {
                    return null;
                }
                        
                @Override
                public int size() {
                    return 0;
                }
            }
            """.trimIndent()
        )
    }

    private fun addLinkedHashSet() {
        myFixture.addClass(
            """
            package java.util;
            
            import java.io.Serializable;
                        
            public class LinkedHashSet<E> extends HashSet<E> implements Set<E>, Cloneable, Serializable {}
            """.trimIndent()
        )
    }
}