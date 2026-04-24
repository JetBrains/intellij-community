// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KtPolySymbolPropertyTypeInspectionTest : LightDevKitInspectionFixTestBase(), ExpectedPluginModeProvider {

  override fun getFileExtension(): String = "kt"

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
  }

  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }

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