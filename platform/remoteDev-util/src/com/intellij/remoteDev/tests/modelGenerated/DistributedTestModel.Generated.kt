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
 * #### Generated from [DistributedTestModel.kt]
 */
class DistributedTestModel private constructor(
    private val _session: RdProperty<RdTestSession?>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            serializers.register(RdAgentInfo)
            serializers.register(RdAgentType.marshaller)
            serializers.register(RdProductType.marshaller)
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
        
        const val serializationHash = 5203196433846444594L
        
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
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val IProtocol.distributedTestModel get() = getOrCreateExtension(DistributedTestModel::class) { @Suppress("DEPRECATION") DistributedTestModel.create(lifetime, this) }



/**
 * #### Generated from [DistributedTestModel.kt]
 */
data class RdAgentInfo (
    val id: String,
    val launchNumber: Int,
    val agentType: RdAgentType,
    val productType: RdProductType
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdAgentInfo> {
        override val _type: KClass<RdAgentInfo> = RdAgentInfo::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdAgentInfo  {
            val id = buffer.readString()
            val launchNumber = buffer.readInt()
            val agentType = buffer.readEnum<RdAgentType>()
            val productType = buffer.readEnum<RdProductType>()
            return RdAgentInfo(id, launchNumber, agentType, productType)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdAgentInfo)  {
            buffer.writeString(value.id)
            buffer.writeInt(value.launchNumber)
            buffer.writeEnum(value.agentType)
            buffer.writeEnum(value.productType)
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
        
        other as RdAgentInfo
        
        if (id != other.id) return false
        if (launchNumber != other.launchNumber) return false
        if (agentType != other.agentType) return false
        if (productType != other.productType) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + launchNumber.hashCode()
        __r = __r*31 + agentType.hashCode()
        __r = __r*31 + productType.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdAgentInfo (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("launchNumber = "); launchNumber.print(printer); println()
            print("agentType = "); agentType.print(printer); println()
            print("productType = "); productType.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [DistributedTestModel.kt]
 */
enum class RdAgentType {
    HOST, 
    CLIENT, 
    GATEWAY;
    
    companion object {
        val marshaller = FrameworkMarshallers.enum<RdAgentType>()
        
    }
}


/**
 * #### Generated from [DistributedTestModel.kt]
 */
enum class RdProductType {
    REMOTE_DEVELOPMENT, 
    CODE_WITH_ME;
    
    companion object {
        val marshaller = FrameworkMarshallers.enum<RdProductType>()
        
    }
}


/**
 * #### Generated from [DistributedTestModel.kt]
 */
class RdTestSession private constructor(
    val agentInfo: RdAgentInfo,
    val testClassName: String?,
    val testMethodName: String?,
    val traceCategories: List<String>,
    val debugCategories: List<String>,
    private val _ready: RdProperty<Boolean?>,
    private val _sendException: RdSignal<RdTestSessionException>,
    private val _shutdown: RdSignal<Unit>,
    private val _showNotification: RdSignal<String>,
    private val _closeProject: RdCall<Unit, Boolean>,
    private val _closeProjectIfOpened: RdCall<Unit, Boolean>,
    private val _runNextAction: RdCall<String, String?>,
    private val _requestFocus: RdCall<String, Boolean>,
    private val _makeScreenshot: RdCall<String, Boolean>,
    private val _isResponding: RdCall<Unit, Boolean>
) : RdBindableBase() {
    //companion
    
    companion object : IMarshaller<RdTestSession> {
        override val _type: KClass<RdTestSession> = RdTestSession::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdTestSession  {
            val _id = RdId.read(buffer)
            val agentInfo = RdAgentInfo.read(ctx, buffer)
            val testClassName = buffer.readNullable { buffer.readString() }
            val testMethodName = buffer.readNullable { buffer.readString() }
            val traceCategories = buffer.readList { buffer.readString() }
            val debugCategories = buffer.readList { buffer.readString() }
            val _ready = RdProperty.read(ctx, buffer, __BoolNullableSerializer)
            val _sendException = RdSignal.read(ctx, buffer, RdTestSessionException)
            val _shutdown = RdSignal.read(ctx, buffer, FrameworkMarshallers.Void)
            val _showNotification = RdSignal.read(ctx, buffer, FrameworkMarshallers.String)
            val _closeProject = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            val _closeProjectIfOpened = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            val _runNextAction = RdCall.read(ctx, buffer, FrameworkMarshallers.String, __StringNullableSerializer)
            val _requestFocus = RdCall.read(ctx, buffer, FrameworkMarshallers.String, FrameworkMarshallers.Bool)
            val _makeScreenshot = RdCall.read(ctx, buffer, FrameworkMarshallers.String, FrameworkMarshallers.Bool)
            val _isResponding = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            return RdTestSession(agentInfo, testClassName, testMethodName, traceCategories, debugCategories, _ready, _sendException, _shutdown, _showNotification, _closeProject, _closeProjectIfOpened, _runNextAction, _requestFocus, _makeScreenshot, _isResponding).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestSession)  {
            value.rdid.write(buffer)
            RdAgentInfo.write(ctx, buffer, value.agentInfo)
            buffer.writeNullable(value.testClassName) { buffer.writeString(it) }
            buffer.writeNullable(value.testMethodName) { buffer.writeString(it) }
            buffer.writeList(value.traceCategories) { v -> buffer.writeString(v) }
            buffer.writeList(value.debugCategories) { v -> buffer.writeString(v) }
            RdProperty.write(ctx, buffer, value._ready)
            RdSignal.write(ctx, buffer, value._sendException)
            RdSignal.write(ctx, buffer, value._shutdown)
            RdSignal.write(ctx, buffer, value._showNotification)
            RdCall.write(ctx, buffer, value._closeProject)
            RdCall.write(ctx, buffer, value._closeProjectIfOpened)
            RdCall.write(ctx, buffer, value._runNextAction)
            RdCall.write(ctx, buffer, value._requestFocus)
            RdCall.write(ctx, buffer, value._makeScreenshot)
            RdCall.write(ctx, buffer, value._isResponding)
        }
        
        private val __BoolNullableSerializer = FrameworkMarshallers.Bool.nullable()
        private val __StringNullableSerializer = FrameworkMarshallers.String.nullable()
        
    }
    //fields
    val ready: IProperty<Boolean?> get() = _ready
    val sendException: IAsyncSignal<RdTestSessionException> get() = _sendException
    val shutdown: IAsyncSignal<Unit> get() = _shutdown
    val showNotification: ISignal<String> get() = _showNotification
    val closeProject: RdCall<Unit, Boolean> get() = _closeProject
    val closeProjectIfOpened: RdCall<Unit, Boolean> get() = _closeProjectIfOpened
    val runNextAction: RdCall<String, String?> get() = _runNextAction
    val requestFocus: RdCall<String, Boolean> get() = _requestFocus
    val makeScreenshot: RdCall<String, Boolean> get() = _makeScreenshot
    val isResponding: RdCall<Unit, Boolean> get() = _isResponding
    //methods
    //initializer
    init {
        _ready.optimizeNested = true
    }
    
    init {
        _sendException.async = true
        _shutdown.async = true
        _closeProject.async = true
        _closeProjectIfOpened.async = true
        _runNextAction.async = true
        _requestFocus.async = true
        _makeScreenshot.async = true
        _isResponding.async = true
    }
    
    init {
        bindableChildren.add("ready" to _ready)
        bindableChildren.add("sendException" to _sendException)
        bindableChildren.add("shutdown" to _shutdown)
        bindableChildren.add("showNotification" to _showNotification)
        bindableChildren.add("closeProject" to _closeProject)
        bindableChildren.add("closeProjectIfOpened" to _closeProjectIfOpened)
        bindableChildren.add("runNextAction" to _runNextAction)
        bindableChildren.add("requestFocus" to _requestFocus)
        bindableChildren.add("makeScreenshot" to _makeScreenshot)
        bindableChildren.add("isResponding" to _isResponding)
    }
    
    //secondary constructor
    constructor(
        agentInfo: RdAgentInfo,
        testClassName: String?,
        testMethodName: String?,
        traceCategories: List<String>,
        debugCategories: List<String>
    ) : this(
        agentInfo,
        testClassName,
        testMethodName,
        traceCategories,
        debugCategories,
        RdProperty<Boolean?>(null, __BoolNullableSerializer),
        RdSignal<RdTestSessionException>(RdTestSessionException),
        RdSignal<Unit>(FrameworkMarshallers.Void),
        RdSignal<String>(FrameworkMarshallers.String),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool),
        RdCall<String, String?>(FrameworkMarshallers.String, __StringNullableSerializer),
        RdCall<String, Boolean>(FrameworkMarshallers.String, FrameworkMarshallers.Bool),
        RdCall<String, Boolean>(FrameworkMarshallers.String, FrameworkMarshallers.Bool),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdTestSession (")
        printer.indent {
            print("agentInfo = "); agentInfo.print(printer); println()
            print("testClassName = "); testClassName.print(printer); println()
            print("testMethodName = "); testMethodName.print(printer); println()
            print("traceCategories = "); traceCategories.print(printer); println()
            print("debugCategories = "); debugCategories.print(printer); println()
            print("ready = "); _ready.print(printer); println()
            print("sendException = "); _sendException.print(printer); println()
            print("shutdown = "); _shutdown.print(printer); println()
            print("showNotification = "); _showNotification.print(printer); println()
            print("closeProject = "); _closeProject.print(printer); println()
            print("closeProjectIfOpened = "); _closeProjectIfOpened.print(printer); println()
            print("runNextAction = "); _runNextAction.print(printer); println()
            print("requestFocus = "); _requestFocus.print(printer); println()
            print("makeScreenshot = "); _makeScreenshot.print(printer); println()
            print("isResponding = "); _isResponding.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): RdTestSession   {
        return RdTestSession(
            agentInfo,
            testClassName,
            testMethodName,
            traceCategories,
            debugCategories,
            _ready.deepClonePolymorphic(),
            _sendException.deepClonePolymorphic(),
            _shutdown.deepClonePolymorphic(),
            _showNotification.deepClonePolymorphic(),
            _closeProject.deepClonePolymorphic(),
            _closeProjectIfOpened.deepClonePolymorphic(),
            _runNextAction.deepClonePolymorphic(),
            _requestFocus.deepClonePolymorphic(),
            _makeScreenshot.deepClonePolymorphic(),
            _isResponding.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
}


/**
 * #### Generated from [DistributedTestModel.kt]
 */
data class RdTestSessionException (
    val type: String,
    val originalType: String?,
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
            val originalType = buffer.readNullable { buffer.readString() }
            val message = buffer.readNullable { buffer.readString() }
            val stacktrace = buffer.readList { RdTestSessionStackTraceElement.read(ctx, buffer) }
            val cause = buffer.readNullable { RdTestSessionExceptionCause.read(ctx, buffer) }
            return RdTestSessionException(type, originalType, message, stacktrace, cause)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestSessionException)  {
            buffer.writeString(value.type)
            buffer.writeNullable(value.originalType) { buffer.writeString(it) }
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
        if (originalType != other.originalType) return false
        if (message != other.message) return false
        if (stacktrace != other.stacktrace) return false
        if (cause != other.cause) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + type.hashCode()
        __r = __r*31 + if (originalType != null) originalType.hashCode() else 0
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
            print("originalType = "); originalType.print(printer); println()
            print("message = "); message.print(printer); println()
            print("stacktrace = "); stacktrace.print(printer); println()
            print("cause = "); cause.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
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
    //threading
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
    //threading
}
