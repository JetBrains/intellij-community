// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServiceProxyGeneratorTest {
  interface MyService {
    fun hello(name: String): String
  }

  class MyServiceImpl : MyService {
    override fun hello(name: String): String = "Hello, $name"
  }

  @Test
  fun testProxyDelegation() {
    val impl = MyServiceImpl()
    val proxy = ServiceProxyGenerator.createInstance(MyService::class.java, impl)

    assertEquals("Hello, World", proxy.hello("World"))
  }

  @Test
  fun testInstrumentation() {
    val impl1 = MyServiceImpl()
    val proxy = ServiceProxyGenerator.createInstance(MyService::class.java, impl1)

    assertTrue(proxy is ServiceProxyInstrumentation)
    val instrumentation = proxy as ServiceProxyInstrumentation

    val impl2 = object : MyService {
      override fun hello(name: String): String = "Hi, $name"
    }

    instrumentation.setForwarding(lazyOf(impl2))
    assertEquals("Hi, World", proxy.hello("World"))

    instrumentation.setForwarding(null)
    assertEquals("Hello, World", proxy.hello("World"))
  }

  abstract class MyAbstractService {
    abstract fun greet(name: String): String
    fun welcome() = "Welcome"
  }

  class MyAbstractServiceImpl : MyAbstractService() {
    override fun greet(name: String): String = "Greetings, $name"
  }

  @Test
  fun testAbstractClassProxy() {
    val impl = MyAbstractServiceImpl()
    val proxy = ServiceProxyGenerator.createInstance(MyAbstractService::class.java, impl)

    assertEquals("Greetings, Alice", proxy.greet("Alice"))
    assertEquals("Welcome", proxy.welcome())
  }

  open class MyBaseClass {
    open fun sayHi() = "Hi from base"
  }

  class MyBaseClassImpl : MyBaseClass() {
    override fun sayHi() = "Hi from impl"
  }

  @Test
  fun testNonAbstractClassProxy() {
    val impl = MyBaseClassImpl()
    val proxy = ServiceProxyGenerator.createInstance(MyBaseClass::class.java, impl)

    assertEquals("Hi from impl", proxy.sayHi())
  }

  interface ComplexService {
    fun voidMethod(list: MutableList<String>)
    fun primitiveParams(i: Int, b: Boolean, d: Double): Int
    fun arrayParams(strings: Array<String>, ints: IntArray): Int
    fun referenceParams(list: List<String>): String
    fun primitiveArrayReturn(): IntArray
  }

  class ComplexServiceImpl : ComplexService {
    override fun voidMethod(list: MutableList<String>) {
      list.add("called")
    }

    override fun primitiveParams(i: Int, b: Boolean, d: Double): Int = if (b) i + d.toInt() else i

    override fun arrayParams(strings: Array<String>, ints: IntArray): Int = strings.size + ints.size

    override fun referenceParams(list: List<String>): String = list.joinToString()

    override fun primitiveArrayReturn(): IntArray = intArrayOf(1, 2, 3)
  }

  @Test
  fun testComplexService() {
    val impl = ComplexServiceImpl()
    val proxy = ServiceProxyGenerator.createInstance(ComplexService::class.java, impl)

    val list = mutableListOf<String>()
    proxy.voidMethod(list)
    assertEquals(listOf("called"), list)

    assertEquals(15, proxy.primitiveParams(10, true, 5.5))
    assertEquals(10, proxy.primitiveParams(10, false, 5.5))

    assertEquals(5, proxy.arrayParams(arrayOf("a", "b"), intArrayOf(1, 2, 3)))
    assertEquals("a, b", proxy.referenceParams(listOf("a", "b")))
    assertArrayEquals(intArrayOf(1, 2, 3), proxy.primitiveArrayReturn())
  }

  @Test
  fun testVisibilityMethods() {
    val impl = TestVisibilityServiceImpl()
    val proxy = ServiceProxyGenerator.createInstance(TestVisibilityService::class.java, impl)

    assertEquals("public_impl", proxy.publicMethod(), "public methods are proxied")
    assertEquals("protected", proxy.callProtected(), "protected methods are not proxied")
    assertEquals("private", proxy.callPrivate(), "override of a private method is not possible")
    assertEquals("package_private", proxy.packagePrivateMethod(), "package-private methods are not proxied")
  }

  interface PrimitiveService {
    fun getLong(): Long
    fun getFloat(): Float
    fun getDouble(): Double
    fun getShort(): Short
    fun getByte(): Byte
    fun getChar(): Char
    fun getBoolean(): Boolean
  }

  class PrimitiveServiceImpl : PrimitiveService {
    override fun getLong(): Long = 1L
    override fun getFloat(): Float = 2.0f
    override fun getDouble(): Double = 3.0
    override fun getShort(): Short = 4
    override fun getByte(): Byte = 5
    override fun getChar(): Char = 'a'
    override fun getBoolean(): Boolean = true
  }

  @Test
  fun testPrimitiveReturns() {
    val impl = PrimitiveServiceImpl()
    val proxy = ServiceProxyGenerator.createInstance(PrimitiveService::class.java, impl)

    assertEquals(1L, proxy.getLong())
    assertEquals(2.0f, proxy.getFloat())
    assertEquals(3.0, proxy.getDouble())
    assertEquals(4.toShort(), proxy.getShort())
    assertEquals(5.toByte(), proxy.getByte())
    assertEquals('a', proxy.getChar())
    assertTrue(proxy.getBoolean())
  }

  interface BaseInterface {
    fun baseMethod(): String
  }

  interface ExtendedInterface : BaseInterface {
    fun extendedMethod(): String
  }

  class ExtendedInterfaceImpl : ExtendedInterface {
    override fun baseMethod(): String = "base"
    override fun extendedMethod(): String = "extended"
  }

  @Test
  fun testInterfaceHierarchy() {
    val impl = ExtendedInterfaceImpl()
    val proxy = ServiceProxyGenerator.createInstance(ExtendedInterface::class.java, impl)

    assertEquals("base", proxy.baseMethod())
    assertEquals("extended", proxy.extendedMethod())
  }

  interface SideInterface {
    fun sideMethod(): Int
  }

  open class BaseServiceClass {
    open fun classMethod(): String = "class"
  }

  class ClassWithInterfaceImpl : BaseServiceClass(), SideInterface {
    override fun sideMethod(): Int = 42
    override fun classMethod(): String = "class_impl"
  }

  @Test
  fun testClassWithInterface() {
    val impl = ClassWithInterfaceImpl()
    // Proxying the class, which also happens to implement an interface
    val proxy = ServiceProxyGenerator.createInstance(BaseServiceClass::class.java, impl)

    assertEquals("class_impl", proxy.classMethod())
    // Even if we proxy BaseServiceClass, if we cast it to SideInterface it should ideally work if the proxy implements it
    // But ServiceProxy only implements the requested superClass and ServiceProxyInstrumentation.
    assertFalse(proxy is SideInterface)
  }

  @Test
  fun testClassWithInterfaceProxiedAsInterface() {
    val impl = ClassWithInterfaceImpl()
    val proxy = ServiceProxyGenerator.createInstance(SideInterface::class.java, impl)

    assertEquals(42, proxy.sideMethod())
  }

  abstract class BaseServiceClassWithSeveralInterfaces : ExtendedInterface, SideInterface {
    open fun classMethod(): String = "class"
  }

  class ClassWithSeveralInterfacesImpl : BaseServiceClassWithSeveralInterfaces() {
    override fun sideMethod(): Int = 42
    override fun classMethod(): String = "class_impl"
    override fun extendedMethod(): String = "extended"
    override fun baseMethod(): String = "base"
  }

  @Test
  fun testBaseServiceClassWithSeveralInterfaces() {
    val impl = ClassWithSeveralInterfacesImpl()
    val proxy = ServiceProxyGenerator.createInstance(BaseServiceClassWithSeveralInterfaces::class.java, impl)

    assertEquals(42, proxy.sideMethod())
    assertEquals("class_impl", proxy.classMethod())
    assertEquals("extended", proxy.extendedMethod())
    assertEquals("base", proxy.baseMethod())
  }

  interface AnyServiceInterface {
    fun baseMethod(): Any
  }

  interface NumberServiceInterface : AnyServiceInterface {
    override fun baseMethod(): Number
  }

  class IntService : NumberServiceInterface {
    override fun baseMethod(): Int = 42
  }

  @Test
  fun testOverriddenMethodsWithMoreSpecificReturnTypes() {
    val impl = IntService()
    val proxy = ServiceProxyGenerator.createInstance(NumberServiceInterface::class.java, impl)
    assertEquals(42, proxy.baseMethod())
  }
}
