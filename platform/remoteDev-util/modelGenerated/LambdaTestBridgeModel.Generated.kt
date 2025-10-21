@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package com.intellij.remoteDev.tests.modelGenerated

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
 * #### Generated from [LambdaTestBridgeModel.kt]
 */
class LambdaTestBridgeModel private constructor(
    private val _syncCall: RdCall<Unit, Unit>,
    private val _sendMessage: RdSignal<String>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
        }
        
        
        @JvmStatic
        @JvmName("internalCreateModel")
        @Deprecated("Use create instead", ReplaceWith("create(lifetime, protocol)"))
        internal fun createModel(lifetime: Lifetime, protocol: IProtocol): LambdaTestBridgeModel  {
            @Suppress("DEPRECATION")
            return create(lifetime, protocol)
        }
        
        @JvmStatic
        @Deprecated("Use protocol.lambdaTestBridgeModel or revise the extension scope instead", ReplaceWith("protocol.lambdaTestBridgeModel"))
        fun create(lifetime: Lifetime, protocol: IProtocol): LambdaTestBridgeModel  {
            LambdaTestRoot.register(protocol.serializers)
            
            return LambdaTestBridgeModel()
        }
        
        
        const val serializationHash = 3868680146025203585L
        
    }
    override val serializersOwner: ISerializersOwner get() = LambdaTestBridgeModel
    override val serializationHash: Long get() = LambdaTestBridgeModel.serializationHash
    
    //fields
    val syncCall: RdCall<Unit, Unit> get() = _syncCall
    
    /**
     * Send message between peers
     */
    val sendMessage: ISignal<String> get() = _sendMessage
    //methods
    //initializer
    init {
        _syncCall.async = true
    }
    
    init {
        bindableChildren.add("syncCall" to _syncCall)
        bindableChildren.add("sendMessage" to _sendMessage)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdCall<Unit, Unit>(FrameworkMarshallers.Void, FrameworkMarshallers.Void),
        RdSignal<String>(FrameworkMarshallers.String)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LambdaTestBridgeModel (")
        printer.indent {
            print("syncCall = "); _syncCall.print(printer); println()
            print("sendMessage = "); _sendMessage.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): LambdaTestBridgeModel   {
        return LambdaTestBridgeModel(
            _syncCall.deepClonePolymorphic(),
            _sendMessage.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val IProtocol.lambdaTestBridgeModel get() = getOrCreateExtension(LambdaTestBridgeModel::class) { @Suppress("DEPRECATION") LambdaTestBridgeModel.create(lifetime, this) }

