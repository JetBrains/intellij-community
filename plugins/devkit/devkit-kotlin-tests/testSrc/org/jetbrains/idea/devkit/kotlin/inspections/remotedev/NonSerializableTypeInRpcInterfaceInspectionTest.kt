// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.remotedev

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

@Language("kotlin")
private const val FLEET_RPC_API = """
package fleet.rpc

interface RemoteApi<Metadata>

@Target(AnnotationTarget.CLASS)
annotation class Rpc
"""

@Language("kotlin")
private const val KOTLIN_SERIALIZABLE_API = """
package kotlinx.serialization

import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.reflect.*

interface KSerializer<T>

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class Serializable(
  val with: KClass<out KSerializer<*>> = KSerializer::class
)

@Target(AnnotationTarget.PROPERTY)
annotation class Transient
"""

class NonSerializableTypeInRpcInterfaceInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(NonSerializableTypeInRpcInterfaceInspection())

    myFixture.addFileToProject("fleet/rpc/Rpc.kt", FLEET_RPC_API)
    myFixture.addFileToProject("kotlinx/serialization/Serializable.kt", KOTLIN_SERIALIZABLE_API)
  }

  fun `test non-serializable type in RPC interface`() {
    myFixture.addKotlinFile(
      "NonSerializableType.kt",
      """
      class NonSerializableType
      """.trimIndent()
    )

    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: <error descr="Non-serializable type 'NonSerializableType' used in an RPC interface API">NonSerializableType</error>): <error descr="Non-serializable type 'NonSerializableType' used in an RPC interface API">NonSerializableType</error>
      }
      """.trimIndent()
    )
  }

  fun `test non-serializable type in RPC interface in parameterized type`() {
    myFixture.addKotlinFile(
      "NonSerializableType.kt",
      """
      class NonSerializableType
      """.trimIndent()
    )

    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: <error descr="Non-serializable type 'List<NonSerializableType>' used in an RPC interface API.
Type reference chain: List<NonSerializableType> → NonSerializableType">List<NonSerializableType></error>): <error descr="Non-serializable type 'Map<String, NonSerializableType>' used in an RPC interface API.
Type reference chain: Map<String, NonSerializableType> → NonSerializableType">Map<String, NonSerializableType></error>
      }
      """.trimIndent()
    )
  }

  fun `test non-serializable type in RPC interface in type's property declared in a primary constructor`() {
    myFixture.addKotlinFile(
      "NonSerializableType.kt",
      """
      class NonSerializableType
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableTypeWithNonSerializableProperty.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Serializable
      class SerializableTypeWithNonSerializableProperty(
        val nonSerializableProperty: NonSerializableType
      ) {
        companion object {
          fun serializer(): KSerializer<SerializableTypeWithNonSerializableProperty> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )

    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: <error descr="Non-serializable property 'SerializableTypeWithNonSerializableProperty.nonSerializableProperty: NonSerializableType' used in an RPC interface API.">SerializableTypeWithNonSerializableProperty</error>): <error descr="Non-serializable property 'SerializableTypeWithNonSerializableProperty.nonSerializableProperty: NonSerializableType' used in an RPC interface API.">SerializableTypeWithNonSerializableProperty</error>
      }
      """.trimIndent()
    )
  }

  fun `test non-serializable type in RPC interface in type's property declared in a class body`() {
    myFixture.addKotlinFile(
      "NonSerializableType.kt",
      """
      class NonSerializableType
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableTypeWithNonSerializableProperty.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Serializable
      class SerializableTypeWithNonSerializableProperty {
        val nonSerializableProperty: NonSerializableType = NonSerializableType()
      
        companion object {
          fun serializer(): KSerializer<SerializableTypeWithNonSerializableProperty> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )

    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: <error descr="Non-serializable property 'SerializableTypeWithNonSerializableProperty.nonSerializableProperty: NonSerializableType' used in an RPC interface API.">SerializableTypeWithNonSerializableProperty</error>): <error descr="Non-serializable property 'SerializableTypeWithNonSerializableProperty.nonSerializableProperty: NonSerializableType' used in an RPC interface API.">SerializableTypeWithNonSerializableProperty</error>
      }
      """.trimIndent()
    )
  }

  fun `test non-serializable type in RPC interface in object's property declared`() {
    myFixture.addKotlinFile(
      "NonSerializableType.kt",
      """
      class NonSerializableType
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableObjectWithNonSerializableProperty.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Serializable
      object SerializableObjectWithNonSerializableProperty {
        val nonSerializableProperty: NonSerializableType = NonSerializableType()
      
        fun serializer(): KSerializer<SerializableObjectWithNonSerializableProperty> { return null /*any; it doesn't matter here*/ }
      }
      """.trimIndent()
    )

    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: <error descr="Non-serializable property 'SerializableObjectWithNonSerializableProperty.nonSerializableProperty: NonSerializableType' used in an RPC interface API.">SerializableObjectWithNonSerializableProperty</error>): <error descr="Non-serializable property 'SerializableObjectWithNonSerializableProperty.nonSerializableProperty: NonSerializableType' used in an RPC interface API.">SerializableObjectWithNonSerializableProperty</error>
      }
      """.trimIndent()
    )
  }

  fun `test non-serializable type in RPC interface in type's property as a type parameter`() {
    myFixture.addKotlinFile(
      "NonSerializableType.kt",
      """
      class NonSerializableType
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableTypeWithNonSerializableProperty.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Serializable
      class SerializableTypeWithNonSerializableProperty(
        val nonSerializableProperty: List<NonSerializableType>
      ) {
        companion object {
          fun serializer(): KSerializer<SerializableTypeWithNonSerializableProperty> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )

    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: <error descr="Non-serializable type 'SerializableTypeWithNonSerializableProperty' used in an RPC interface API.
Type reference chain: SerializableTypeWithNonSerializableProperty → List<NonSerializableType> → NonSerializableType">SerializableTypeWithNonSerializableProperty</error>): <error descr="Non-serializable type 'SerializableTypeWithNonSerializableProperty' used in an RPC interface API.
Type reference chain: SerializableTypeWithNonSerializableProperty → List<NonSerializableType> → NonSerializableType">SerializableTypeWithNonSerializableProperty</error>
      }
      """.trimIndent()
    )
  }

  fun `test non-serializable type in RPC interface in type's property of List type preceded with another List that is serializable`() {
    // the purpose of this test is to check that collecting checked types include type arguments, not only the main type
    // and further types are checked
    myFixture.addKotlinFile(
      "NonSerializableType.kt",
      """
      class NonSerializableType
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableTypeWithNonSerializableProperty.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Serializable
      class SerializableTypeWithNonSerializableProperty(
        val serializableProperty: List<Int> // serializable
        val nonSerializableProperty: List<NonSerializableType>
      ) {
        companion object {
          fun serializer(): KSerializer<SerializableTypeWithNonSerializableProperty> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )

    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: <error descr="Non-serializable type 'SerializableTypeWithNonSerializableProperty' used in an RPC interface API.
Type reference chain: SerializableTypeWithNonSerializableProperty → List<NonSerializableType> → NonSerializableType">SerializableTypeWithNonSerializableProperty</error>): <error descr="Non-serializable type 'SerializableTypeWithNonSerializableProperty' used in an RPC interface API.
Type reference chain: SerializableTypeWithNonSerializableProperty → List<NonSerializableType> → NonSerializableType">SerializableTypeWithNonSerializableProperty</error>
      }
      """.trimIndent()
    )
  }

  fun `test non-serializable type in RPC interface in type's property's type argument`() {
    myFixture.addKotlinFile(
      "NonSerializableType.kt",
      """
      class NonSerializableType
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableTypeWithNonSerializableProperty.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Serializable
      class SerializableTypeWithNonSerializableProperty(
        val nonSerializableProperty: NonSerializableType
      ) {
        companion object {
          fun serializer(): KSerializer<SerializableTypeWithNonSerializableProperty> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableTypeWithPropertyOfGenericTypeParameterizedByNonSerializableType.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Serializable
      class SerializableTypeWithPropertyOfGenericTypeParameterizedByNonSerializableType(
        val property: List<SerializableTypeWithNonSerializableProperty>
      ) {
        companion object {
          fun serializer(): KSerializer<SerializableTypeWithPropertyOfGenericTypeParameterizedByNonSerializableType> { 
            return null /*any; it doesn't matter here*/ 
          }
        }
      }
      """.trimIndent()
    )

    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: <error descr="Non-serializable property 'SerializableTypeWithNonSerializableProperty.nonSerializableProperty: NonSerializableType' used in an RPC interface API.
Type reference chain: SerializableTypeWithPropertyOfGenericTypeParameterizedByNonSerializableType → List<SerializableTypeWithNonSerializableProperty> → SerializableTypeWithNonSerializableProperty">SerializableTypeWithPropertyOfGenericTypeParameterizedByNonSerializableType</error>): <error descr="Non-serializable property 'SerializableTypeWithNonSerializableProperty.nonSerializableProperty: NonSerializableType' used in an RPC interface API.
Type reference chain: SerializableTypeWithPropertyOfGenericTypeParameterizedByNonSerializableType → List<SerializableTypeWithNonSerializableProperty> → SerializableTypeWithNonSerializableProperty">SerializableTypeWithPropertyOfGenericTypeParameterizedByNonSerializableType</error>
      }
      """.trimIndent()
    )
  }

  fun `test should not report types serializable by default`() {
    myFixture.addKotlinFile(
      "Duration.kt",
      """
      package kotlin.time
      class Duration
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableType.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      import kotlin.time.Duration
      
      @Serializable
      class SerializableType(    
        val boolean: Boolean,
        val byte: Byte,
        val char: Char,
        val double: Double,
        val float: Float,
        val int: Int,
        val long: Long,
        val short: Short,
      
        val uInt: UInt,
        val uLong: ULong,
        val uByte: UByte,
        val uShort: UShort,
      
        val string: String,
        val pair: Pair,
        val triple: Triple,
        val unit: Unit,
        val duration: Duration,
      
        val collection: Collection
        val list: List
        val set: Set
        val map: Map
        val array: Array
      
        val booleanArray: BooleanArray
        val byteArray: ByteArray
        val charArray: CharArray
        val doubleArray: DoubleArray
        val floatArray: FloatArray
        val intArray: IntArray
        val longArray: LongArray
        val shortArray: ShortArray
      
        val uByteArray: UByteArray
        val uIntArray: UIntArray
        val uLongArray: ULongArray
        val uShortArray: UShortArray
      ) {
        companion object {
          fun serializer(): KSerializer<SerializableType> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )
    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: SerializableType): SerializableType
      }
      """.trimIndent()
    )
  }

  fun `test should not report coroutine-related and supported types`() {
    myFixture.addKotlinFile(
      "Channel.kt",
      """
      package kotlinx.coroutines.channels
      
      interface ReceiveChannel<out E>
      interface SendChannel<in E>
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "Deferred.kt",
      """
      package kotlinx.coroutines
      
      interface Deferred<out T>
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "Flow.kt",
      """
      package kotlinx.coroutines.flow
      
      interface Flow<out T>
      """.trimIndent()
    )
    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      import kotlinx.coroutines.Deferred
      import kotlinx.coroutines.flow.Flow
      import kotlinx.coroutines.channels.ReceiveChannel
      import kotlinx.coroutines.channels.SendChannel
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test1(): Deferred<Unit>
        suspend fun test2(): Flow<Unit>
        suspend fun test3(): ReceiveChannel<Unit>
        suspend fun test4(): SendChannel<Unit>
      }
      """.trimIndent()
    )
  }

  fun `test should not report enums`() {  // do not report enums until IJPL-190471 is clarified
    myFixture.addKotlinFile(
      "MyEnum.kt",
      """ 
      enum class MyEnum {
        VALUE1, VALUE2
      }
      """.trimIndent())
    myFixture.addKotlinFile(
      "SerializableType.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Serializable
      class SerializableType(    
        val myEnum: MyEnum
      ) {
        companion object {
          fun serializer(): KSerializer<SerializableType> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )
    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param1: MyEnum, param2: SerializableType): SerializableType
      }
      """.trimIndent()
    )
  }

  fun `test should not report non-serializable Transient properties`() {
    myFixture.addKotlinFile(
      "NonSerializableType.kt",
      """
      class NonSerializableType
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableType.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.Transient
      import kotlinx.serialization.KSerializer
      
      @Serializable
      class SerializableType(    
        @Transient
        val nonSerializable: NonSerializableType
      ) {
        companion object {
          fun serializer(): KSerializer<SerializableType> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )
    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: SerializableType): SerializableType
      }
      """.trimIndent()
    )
  }

  fun `test should not report DurableRef`() {
    myFixture.addKotlinFile(
      "fleet/kernel.DurableEntity.kt",
      """
      package fleet.kernel
      import kotlinx.serialization.Serializable
      
      @Serializable
      data class DurableRef<T>
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "RhizomeEntity.kt",
      """
      class RhizomeEntity // Rhizome classes are not analysed
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableType.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.Transient
      import kotlinx.serialization.KSerializer
      import fleet.kernel.DurableRef
      
      @Serializable
      class SerializableType(
        val durableRef: DurableRef<RhizomeEntity>
      ) {
        companion object {
          fun serializer(): KSerializer<SerializableType> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )
    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: SerializableType): SerializableType
      }
      """.trimIndent()
    )
  }

  fun `test abstract property of Any type should not be reported as it can be overridden by a serializable property`() {
    myFixture.addKotlinFile(
      "AbstractType.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Serializable
      abstract class AbstractType {
        abstract val abstractProperty: Any
        
        companion object {
          fun serializer(): KSerializer<AbstractType> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "SerializableTypeWithAbstractTypeProperty.kt",
      """
      import kotlinx.serialization.Serializable
      import kotlinx.serialization.KSerializer
      
      @Serializable
      class SerializableTypeWithAbstractTypeProperty(
        val abstractTypeProperty: AbstractType
      ) {
        companion object {
          fun serializer(): KSerializer<SerializableTypeWithAbstractTypeProperty> { return null /*any; it doesn't matter here*/ }
        }
      }
      """.trimIndent()
    )

    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test(param: SerializableTypeWithAbstractTypeProperty): SerializableTypeWithAbstractTypeProperty
      }
      """.trimIndent()
    )
  }

  fun `test should not report remote services in Fleet code`() {
    @Suppress("SSBasedInspection")
    myFixture.addKotlinFile(
      "fleet/rpc/core/RemoteResource.kt",
      """
      package fleet.rpc.core
      
      import fleet.rpc.RemoteApi
      
      interface RemoteResource : RemoteApi<Unit>
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "fleet/rpc/core/RemoteObject.kt",
      """
      package fleet.rpc.core
      
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RemoteObject : RemoteApi<Unit> {
      """.trimIndent()
    )
    myFixture.addKotlinFile(
      "fleet/util/async/Resource.kt",
      """
      package fleet.util.async
      
      interface Resource<out T>
      """.trimIndent()
    )

    testHighlighting(
      "fleet/myModule/RpcInterface.kt",
      """
      import fleet.rpc.core.RemoteResource
      import fleet.rpc.core.RemoteObject
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      import fleet.util.async.Resource
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun test1(): Resource<RemoteSession>
        suspend fun test2(): RemoteObjectSession
      }
      
      @Rpc
      interface RemoteSession : RemoteResource        
      
      @Rpc
      interface RemoteObjectSession : RemoteObject        
      """.trimIndent()
    )
  }

  // TODO: serializer in object

  private fun testHighlighting(@Language("kotlin") code: String) {
    testHighlighting("RpcInterface.kt", code)
  }

  private fun testHighlighting(relativePath: String, @Language("kotlin") code: String) {
    myFixture.testHighlighting(true, true, true, myFixture.addKotlinFile(relativePath, code).virtualFile)
  }

  private fun CodeInsightTestFixture.addKotlinFile(relativePath: String, @Language("kotlin") fileText: String): PsiFile {
    return this.addFileToProject(relativePath, fileText)
  }

}
