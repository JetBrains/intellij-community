// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The tests of [KtPolySymbolPropertyTypeInspection]. Each subclass runs them in the project that it gives to the constructor.
 * The project must have the Kotlin standard library, for example from [polySymbolPropertyTypeProjectFixture].
 */
abstract class KtPolySymbolPropertyTypeInspectionTest(projectFixture: TestFixture<LightJavaProject>) {
  private val devKit by lightDevKitInspectionFixture(projectFixture, "kt")
  private val myFixture get() = devKit.fixture

  @BeforeEach
  fun setUpPolySymbolClasses() {
    myFixture.enableInspections(KtPolySymbolPropertyTypeInspection())

    // Add necessary infrastructure classes
    myFixture.configureByText("PolySymbol.kt", """
      package com.intellij.polySymbols
      
      abstract class PolySymbol {
        @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
        @Retention(AnnotationRetention.RUNTIME)
        annotation class Property(val property: kotlin.reflect.KClass<*>)
      }
      
      abstract class PolySymbolProperty<T : Any>(val name: String, val type: Class<T>)
    """.trimIndent())
  }

  @Test
  fun testCompatibleType() {
    myFixture.configureByText("Test.kt", """
      package test
      
      import com.intellij.polySymbols.PolySymbol
      import com.intellij.polySymbols.PolySymbolProperty
      
      class MyProperty : PolySymbolProperty<String>("test", String::class.java)
      
      class MySymbol : PolySymbol() {
        @PolySymbol.Property(MyProperty::class)
        private val myProp: String = "test"
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun testIncompatibleType() {
    myFixture.configureByText("Test.kt", """
      package test
      
      import com.intellij.polySymbols.PolySymbol
      import com.intellij.polySymbols.PolySymbolProperty
      
      class MyProperty : PolySymbolProperty<String>("test", String::class.java)
      
      class MySymbol : PolySymbol() {
        @PolySymbol.Property(MyProperty::class)
        private val <error descr="Declared type 'Int' is not assignable to the expected PolySymbol property type 'String'">myProp</error>: Int = 42
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun testSubtypeIsCompatible() {
    myFixture.configureByText("Test.kt", """
      package test
      
      import com.intellij.polySymbols.PolySymbol
      import com.intellij.polySymbols.PolySymbolProperty
      
      open class Base
      class Derived : Base()
      
      class MyProperty : PolySymbolProperty<Base>("test", Base::class.java)
      
      class MySymbol : PolySymbol() {
        @PolySymbol.Property(MyProperty::class)
        private val myProp: Derived = Derived()
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun testNullableTypeCompatible() {
    myFixture.configureByText("Test.kt", """
      package test
      
      import com.intellij.polySymbols.PolySymbol
      import com.intellij.polySymbols.PolySymbolProperty
      
      class MyProperty : PolySymbolProperty<String>("test", String::class.java)
      
      class MySymbol : PolySymbol() {
        @PolySymbol.Property(MyProperty::class)
        private val myProp: String? = null
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun testPropertyGetter() {
    myFixture.configureByText("Test.kt", """
      package test
      
      import com.intellij.polySymbols.PolySymbol
      import com.intellij.polySymbols.PolySymbolProperty
      
      class MyProperty : PolySymbolProperty<String>("test", String::class.java)
      
      class MySymbol : PolySymbol() {
        @PolySymbol.Property(MyProperty::class)
        private val myProp: String
          get() = "test"
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun testPrimaryConstructorProperty() {
    myFixture.configureByText("Test.kt", """
      package test
      
      import com.intellij.polySymbols.PolySymbol
      import com.intellij.polySymbols.PolySymbolProperty
      
      class MyProperty : PolySymbolProperty<String>("test", String::class.java)
      
      class MySymbol(
        @PolySymbol.Property(MyProperty::class)
        private val <error descr="Declared type 'Int' is not assignable to the expected PolySymbol property type 'String'">myProp</error>: Int
      ) : PolySymbol()
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun testRealWorldExample_TypeScriptTypeMember() {
    myFixture.configureByText("PropertySignature.kt", """
      package com.intellij.lang.javascript.psi
      interface PropertySignature
    """.trimIndent())

    myFixture.configureByText("TypeScriptTypeMember.kt", """
      package com.intellij.lang.javascript.psi.ecma6
      open class TypeScriptTypeMember
    """.trimIndent())

    myFixture.configureByText("JSPropertySignatureProperty.kt", """
      package com.intellij.polySymbols.js
      
      import com.intellij.polySymbols.PolySymbolProperty
      import com.intellij.lang.javascript.psi.PropertySignature
      
      object JSPropertySignatureProperty : PolySymbolProperty<PropertySignature>("js-property-signature", PropertySignature::class.java)
    """.trimIndent())

    myFixture.configureByText("Test.kt", """
      package test
      
      import com.intellij.polySymbols.PolySymbol
      import com.intellij.polySymbols.js.JSPropertySignatureProperty
      import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeMember
      
      class TypeScriptTypeMemberSymbol : PolySymbol() {
        @PolySymbol.Property(JSPropertySignatureProperty::class)
        private val <error descr="Declared type 'TypeScriptTypeMember' is not assignable to the expected PolySymbol property type 'PropertySignature'">propertySignature</error>: TypeScriptTypeMember
          get() = TODO()
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}

/**
 * Creates the project of a [KtPolySymbolPropertyTypeInspectionTest] subclass: the JDK and the Kotlin standard library.
 * Declare it in a companion object.
 */
fun polySymbolPropertyTypeProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance())