@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package com.intellij.remoteDev.util.tests.modelGenerated

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.*
import com.jetbrains.rd.framework.impl.*

import com.jetbrains.rd.util.lifetime.*
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.string.*
import com.jetbrains.rd.util.*
import kotlin.time.Duration
import kotlin.reflect.KClass
import kotlin.jvm.JvmStatic



/**
 * #### Generated from [DistributedTestBridgeModel.kt]
 */
class DistributedTestBridgeModel private constructor(
    private val _syncCall: RdCall<Unit, Unit>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
        }
        
        
        @JvmStatic
        @JvmName("internalCreateModel")
        @Deprecated("Use create instead", ReplaceWith("create(lifetime, protocol)"))
        internal fun createModel(lifetime: Lifetime, protocol: IProtocol): DistributedTestBridgeModel  {
            @Suppress("DEPRECATION")
            return create(lifetime, protocol)
        }
        
        @JvmStatic
        @Deprecated("Use protocol.distributedTestBridgeModel or revise the extension scope instead", ReplaceWith("protocol.distributedTestBridgeModel"))
        fun create(lifetime: Lifetime, protocol: IProtocol): DistributedTestBridgeModel  {
            TestRoot.register(protocol.serializers)
            
            return DistributedTestBridgeModel()
        }
        
        
        const val serializationHash = -6785668080277295037L
        
    }
    override val serializersOwner: ISerializersOwner get() = DistributedTestBridgeModel
    override val serializationHash: Long get() = DistributedTestBridgeModel.serializationHash
    
    //fields
    val syncCall: RdCall<Unit, Unit> get() = _syncCall
    //methods
    //initializer
    init {
        bindableChildren.add("syncCall" to _syncCall)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdCall<Unit, Unit>(FrameworkMarshallers.Void, FrameworkMarshallers.Void)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("DistributedTestBridgeModel (")
        printer.indent {
            print("syncCall = "); _syncCall.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): DistributedTestBridgeModel   {
        return DistributedTestBridgeModel(
            _syncCall.deepClonePolymorphic()
        )
    }
    //contexts
}
val IProtocol.distributedTestBridgeModel get() = getOrCreateExtension(DistributedTestBridgeModel::class) { @Suppress("DEPRECATION") DistributedTestBridgeModel.create(lifetime, this) }

