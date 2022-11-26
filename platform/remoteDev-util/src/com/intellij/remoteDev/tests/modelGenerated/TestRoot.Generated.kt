@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package com.intellij.remoteDev.tests.modelGenerated

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.*
import com.jetbrains.rd.framework.impl.*

import com.jetbrains.rd.util.lifetime.*
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.string.*
import com.jetbrains.rd.util.*
import kotlin.reflect.KClass
import kotlin.jvm.JvmStatic



/**
 * #### Generated from [DistributedTestModel.kt]
 */
class TestRoot private constructor(
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            TestRoot.register(serializers)
            DistributedTestBridgeModel.register(serializers)
            DistributedTestModel.register(serializers)
        }
        
        
        
        
        
        const val serializationHash = 2990581243564688278L
        
    }
    override val serializersOwner: ISerializersOwner get() = TestRoot
    override val serializationHash: Long get() = TestRoot.serializationHash
    
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("TestRoot (")
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): TestRoot   {
        return TestRoot(
        )
    }
    //contexts
}
