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
 * #### Generated from [DistributedTestModel.kt]
 */
class DistributedTestModel private constructor(
    private val _session: RdProperty<RdTestSession?>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            serializers.register(RdAgentId)
            serializers.register(RdTestSessionStackTraceElement)
            serializers.register(RdTestSessionExceptionCause)
            serializers.register(RdTestSessionException)
            serializers.register(RdTestSession)
        }
        
        
        @JvmStatic
        @JvmName("internalCreateModel")
        @Deprecated("Use create instead", ReplaceWith("create(lifetime, protocol)"))
        internal fun createModel(lifetime: Lifetime, protocol: IProtocol): DistributedTestModel  {
            @Suppress("DEPRECATION")
            return create(lifetime, protocol)
        }
        
        @JvmStatic
        @Deprecated("Use protocol.distributedTestModel or revise the extension scope instead", ReplaceWith("protocol.distributedTestModel"))
        fun create(lifetime: Lifetime, protocol: IProtocol): DistributedTestModel  {
            TestRoot.register(protocol.serializers)
            
            return DistributedTestModel()
        }
        
        private val __RdTestSessionNullableSerializer = RdTestSession.nullable()
        
        const val serializationHash = -603340022673479212L
        
    }
    override val serializersOwner: ISerializersOwner get() = DistributedTestModel
    override val serializationHash: Long get() = DistributedTestModel.serializationHash
    
    //fields
    val session: IProperty<RdTestSession?> get() = _session
    //methods
    //initializer
    init {
        bindableChildren.add("session" to _session)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdProperty<RdTestSession?>(null, __RdTestSessionNullableSerializer)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("DistributedTestModel (")
        printer.indent {
            print("session = "); _session.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): DistributedTestModel   {
        return DistributedTestModel(
            _session.deepClonePolymorphic()
        )
    }
    //contexts
}
val IProtocol.distributedTestModel get() = getOrCreateExtension(DistributedTestModel::class) { @Suppress("DEPRECATION") DistributedTestModel.create(lifetime, this) }



/**
 * #### Generated from [DistributedTestModel.kt]
 */
data class RdAgentId (
    val id: String,
    val launchNumber: Int
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdAgentId> {
        override val _type: KClass<RdAgentId> = RdAgentId::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdAgentId  {
            val id = buffer.readString()
            val launchNumber = buffer.readInt()
            return RdAgentId(id, launchNumber)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdAgentId)  {
            buffer.writeString(value.id)
            buffer.writeInt(value.launchNumber)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as RdAgentId
        
        if (id != other.id) return false
        if (launchNumber != other.launchNumber) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + launchNumber.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdAgentId (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("launchNumber = "); launchNumber.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}


/**
 * #### Generated from [DistributedTestModel.kt]
 */
class RdTestSession private constructor(
    val agentId: RdAgentId,
    val testClassName: String,
    val testMethodName: String,
    val traceCategories: List<String>,
    private val _ready: RdProperty<Boolean?>,
    private val _sendException: RdSignal<RdTestSessionException>,
    private val _shutdown: RdSignal<Unit>,
    private val _dumpThreads: RdSignal<Unit>,
    private val _runNextAction: RdCall<Unit, Boolean>,
    private val _makeScreenshot: RdCall<String, Boolean>
) : RdBindableBase() {
    //companion
    
    companion object : IMarshaller<RdTestSession> {
        override val _type: KClass<RdTestSession> = RdTestSession::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdTestSession  {
            val _id = RdId.read(buffer)
            val agentId = RdAgentId.read(ctx, buffer)
            val testClassName = buffer.readString()
            val testMethodName = buffer.readString()
            val traceCategories = buffer.readList { buffer.readString() }
            val _ready = RdProperty.read(ctx, buffer, __BoolNullableSerializer)
            val _sendException = RdSignal.read(ctx, buffer, RdTestSessionException)
            val _shutdown = RdSignal.read(ctx, buffer, FrameworkMarshallers.Void)
            val _dumpThreads = RdSignal.read(ctx, buffer, FrameworkMarshallers.Void)
            val _runNextAction = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            val _makeScreenshot = RdCall.read(ctx, buffer, FrameworkMarshallers.String, FrameworkMarshallers.Bool)
            return RdTestSession(agentId, testClassName, testMethodName, traceCategories, _ready, _sendException, _shutdown, _dumpThreads, _runNextAction, _makeScreenshot).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestSession)  {
            value.rdid.write(buffer)
            RdAgentId.write(ctx, buffer, value.agentId)
            buffer.writeString(value.testClassName)
            buffer.writeString(value.testMethodName)
            buffer.writeList(value.traceCategories) { v -> buffer.writeString(v) }
            RdProperty.write(ctx, buffer, value._ready)
            RdSignal.write(ctx, buffer, value._sendException)
            RdSignal.write(ctx, buffer, value._shutdown)
            RdSignal.write(ctx, buffer, value._dumpThreads)
            RdCall.write(ctx, buffer, value._runNextAction)
            RdCall.write(ctx, buffer, value._makeScreenshot)
        }
        
        private val __BoolNullableSerializer = FrameworkMarshallers.Bool.nullable()
        
    }
    //fields
    val ready: IProperty<Boolean?> get() = _ready
    val sendException: ISignal<RdTestSessionException> get() = _sendException
    val shutdown: ISignal<Unit> get() = _shutdown
    val dumpThreads: IAsyncSignal<Unit> get() = _dumpThreads
    val runNextAction: RdCall<Unit, Boolean> get() = _runNextAction
    val makeScreenshot: RdCall<String, Boolean> get() = _makeScreenshot
    //methods
    //initializer
    init {
        _ready.optimizeNested = true
    }
    
    init {
        _dumpThreads.async = true
    }
    
    init {
        bindableChildren.add("ready" to _ready)
        bindableChildren.add("sendException" to _sendException)
        bindableChildren.add("shutdown" to _shutdown)
        bindableChildren.add("dumpThreads" to _dumpThreads)
        bindableChildren.add("runNextAction" to _runNextAction)
        bindableChildren.add("makeScreenshot" to _makeScreenshot)
    }
    
    //secondary constructor
    constructor(
        agentId: RdAgentId,
        testClassName: String,
        testMethodName: String,
        traceCategories: List<String>
    ) : this(
        agentId,
        testClassName,
        testMethodName,
        traceCategories,
        RdProperty<Boolean?>(null, __BoolNullableSerializer),
        RdSignal<RdTestSessionException>(RdTestSessionException),
        RdSignal<Unit>(FrameworkMarshallers.Void),
        RdSignal<Unit>(FrameworkMarshallers.Void),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool),
        RdCall<String, Boolean>(FrameworkMarshallers.String, FrameworkMarshallers.Bool)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdTestSession (")
        printer.indent {
            print("agentId = "); agentId.print(printer); println()
            print("testClassName = "); testClassName.print(printer); println()
            print("testMethodName = "); testMethodName.print(printer); println()
            print("traceCategories = "); traceCategories.print(printer); println()
            print("ready = "); _ready.print(printer); println()
            print("sendException = "); _sendException.print(printer); println()
            print("shutdown = "); _shutdown.print(printer); println()
            print("dumpThreads = "); _dumpThreads.print(printer); println()
            print("runNextAction = "); _runNextAction.print(printer); println()
            print("makeScreenshot = "); _makeScreenshot.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): RdTestSession   {
        return RdTestSession(
            agentId,
            testClassName,
            testMethodName,
            traceCategories,
            _ready.deepClonePolymorphic(),
            _sendException.deepClonePolymorphic(),
            _shutdown.deepClonePolymorphic(),
            _dumpThreads.deepClonePolymorphic(),
            _runNextAction.deepClonePolymorphic(),
            _makeScreenshot.deepClonePolymorphic()
        )
    }
    //contexts
}


/**
 * #### Generated from [DistributedTestModel.kt]
 */
data class RdTestSessionException (
    val type: String,
    val message: String?,
    val stacktrace: List<RdTestSessionStackTraceElement>,
    val cause: RdTestSessionExceptionCause?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdTestSessionException> {
        override val _type: KClass<RdTestSessionException> = RdTestSessionException::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdTestSessionException  {
            val type = buffer.readString()
            val message = buffer.readNullable { buffer.readString() }
            val stacktrace = buffer.readList { RdTestSessionStackTraceElement.read(ctx, buffer) }
            val cause = buffer.readNullable { RdTestSessionExceptionCause.read(ctx, buffer) }
            return RdTestSessionException(type, message, stacktrace, cause)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestSessionException)  {
            buffer.writeString(value.type)
            buffer.writeNullable(value.message) { buffer.writeString(it) }
            buffer.writeList(value.stacktrace) { v -> RdTestSessionStackTraceElement.write(ctx, buffer, v) }
            buffer.writeNullable(value.cause) { RdTestSessionExceptionCause.write(ctx, buffer, it) }
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as RdTestSessionException
        
        if (type != other.type) return false
        if (message != other.message) return false
        if (stacktrace != other.stacktrace) return false
        if (cause != other.cause) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + type.hashCode()
        __r = __r*31 + if (message != null) message.hashCode() else 0
        __r = __r*31 + stacktrace.hashCode()
        __r = __r*31 + if (cause != null) cause.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdTestSessionException (")
        printer.indent {
            print("type = "); type.print(printer); println()
            print("message = "); message.print(printer); println()
            print("stacktrace = "); stacktrace.print(printer); println()
            print("cause = "); cause.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}


/**
 * #### Generated from [DistributedTestModel.kt]
 */
data class RdTestSessionExceptionCause (
    val type: String,
    val message: String?,
    val stacktrace: List<RdTestSessionStackTraceElement>
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdTestSessionExceptionCause> {
        override val _type: KClass<RdTestSessionExceptionCause> = RdTestSessionExceptionCause::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdTestSessionExceptionCause  {
            val type = buffer.readString()
            val message = buffer.readNullable { buffer.readString() }
            val stacktrace = buffer.readList { RdTestSessionStackTraceElement.read(ctx, buffer) }
            return RdTestSessionExceptionCause(type, message, stacktrace)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestSessionExceptionCause)  {
            buffer.writeString(value.type)
            buffer.writeNullable(value.message) { buffer.writeString(it) }
            buffer.writeList(value.stacktrace) { v -> RdTestSessionStackTraceElement.write(ctx, buffer, v) }
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as RdTestSessionExceptionCause
        
        if (type != other.type) return false
        if (message != other.message) return false
        if (stacktrace != other.stacktrace) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + type.hashCode()
        __r = __r*31 + if (message != null) message.hashCode() else 0
        __r = __r*31 + stacktrace.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdTestSessionExceptionCause (")
        printer.indent {
            print("type = "); type.print(printer); println()
            print("message = "); message.print(printer); println()
            print("stacktrace = "); stacktrace.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}


/**
 * #### Generated from [DistributedTestModel.kt]
 */
data class RdTestSessionStackTraceElement (
    val declaringClass: String,
    val methodName: String,
    val fileName: String,
    val lineNumber: Int
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdTestSessionStackTraceElement> {
        override val _type: KClass<RdTestSessionStackTraceElement> = RdTestSessionStackTraceElement::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdTestSessionStackTraceElement  {
            val declaringClass = buffer.readString()
            val methodName = buffer.readString()
            val fileName = buffer.readString()
            val lineNumber = buffer.readInt()
            return RdTestSessionStackTraceElement(declaringClass, methodName, fileName, lineNumber)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestSessionStackTraceElement)  {
            buffer.writeString(value.declaringClass)
            buffer.writeString(value.methodName)
            buffer.writeString(value.fileName)
            buffer.writeInt(value.lineNumber)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as RdTestSessionStackTraceElement
        
        if (declaringClass != other.declaringClass) return false
        if (methodName != other.methodName) return false
        if (fileName != other.fileName) return false
        if (lineNumber != other.lineNumber) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + declaringClass.hashCode()
        __r = __r*31 + methodName.hashCode()
        __r = __r*31 + fileName.hashCode()
        __r = __r*31 + lineNumber.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdTestSessionStackTraceElement (")
        printer.indent {
            print("declaringClass = "); declaringClass.print(printer); println()
            print("methodName = "); methodName.print(printer); println()
            print("fileName = "); fileName.print(printer); println()
            print("lineNumber = "); lineNumber.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}
