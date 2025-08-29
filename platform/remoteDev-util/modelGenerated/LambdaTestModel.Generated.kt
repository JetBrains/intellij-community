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
 * #### Generated from [LambdaTestModel.kt]
 */
class LambdaTestModel private constructor(
    private val _session: RdProperty<LambdaRdTestSession?>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            val classLoader = javaClass.classLoader
            serializers.register(LazyCompanionMarshaller(RdId(-3520458110972730548), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeInfo"))
            serializers.register(LazyCompanionMarshaller(RdId(-3520458110972391976), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType"))
            serializers.register(LazyCompanionMarshaller(RdId(-8183511780297815289), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSessionStackTraceElement"))
            serializers.register(LazyCompanionMarshaller(RdId(-1877965166079974414), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSessionExceptionCause"))
            serializers.register(LazyCompanionMarshaller(RdId(-1075846985405547849), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSessionException"))
            serializers.register(LazyCompanionMarshaller(RdId(-3702464714964495074), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestActionParameters"))
            serializers.register(LazyCompanionMarshaller(RdId(3210199037986225272), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession"))
        }
        
        
        @JvmStatic
        @JvmName("internalCreateModel")
        @Deprecated("Use create instead", ReplaceWith("create(lifetime, protocol)"))
        internal fun createModel(lifetime: Lifetime, protocol: IProtocol): LambdaTestModel  {
            @Suppress("DEPRECATION")
            return create(lifetime, protocol)
        }
        
        @JvmStatic
        @Deprecated("Use protocol.lambdaTestModel or revise the extension scope instead", ReplaceWith("protocol.lambdaTestModel"))
        fun create(lifetime: Lifetime, protocol: IProtocol): LambdaTestModel  {
            LambdaTestRoot.register(protocol.serializers)
            
            return LambdaTestModel()
        }
        
        private val __LambdaRdTestSessionNullableSerializer = LambdaRdTestSession.nullable()
        
        const val serializationHash = 6458896133684569706L
        
    }
    override val serializersOwner: ISerializersOwner get() = LambdaTestModel
    override val serializationHash: Long get() = LambdaTestModel.serializationHash
    
    //fields
    val session: IProperty<LambdaRdTestSession?> get() = _session
    //methods
    //initializer
    init {
        bindableChildren.add("session" to _session)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdProperty<LambdaRdTestSession?>(null, __LambdaRdTestSessionNullableSerializer)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LambdaTestModel (")
        printer.indent {
            print("session = "); _session.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): LambdaTestModel   {
        return LambdaTestModel(
            _session.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val IProtocol.lambdaTestModel get() = getOrCreateExtension(LambdaTestModel::class) { @Suppress("DEPRECATION") LambdaTestModel.create(lifetime, this) }



/**
 * #### Generated from [LambdaTestModel.kt]
 */
data class LambdaRdIdeInfo (
    val id: String,
    val ideType: LambdaRdIdeType,
    val testClassName: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<LambdaRdIdeInfo> {
        override val _type: KClass<LambdaRdIdeInfo> = LambdaRdIdeInfo::class
        override val id: RdId get() = RdId(-3520458110972730548)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LambdaRdIdeInfo  {
            val id = buffer.readString()
            val ideType = buffer.readEnum<LambdaRdIdeType>()
            val testClassName = buffer.readString()
            return LambdaRdIdeInfo(id, ideType, testClassName)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LambdaRdIdeInfo)  {
            buffer.writeString(value.id)
            buffer.writeEnum(value.ideType)
            buffer.writeString(value.testClassName)
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
        
        other as LambdaRdIdeInfo
        
        if (id != other.id) return false
        if (ideType != other.ideType) return false
        if (testClassName != other.testClassName) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + ideType.hashCode()
        __r = __r*31 + testClassName.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LambdaRdIdeInfo (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("ideType = "); ideType.print(printer); println()
            print("testClassName = "); testClassName.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [LambdaTestModel.kt]
 */
enum class LambdaRdIdeType {
    BACKEND, 
    FRONTEND, 
    MONOLITH;
    
    companion object : IMarshaller<LambdaRdIdeType> {
        val marshaller = FrameworkMarshallers.enum<LambdaRdIdeType>()
        
        
        override val _type: KClass<LambdaRdIdeType> = LambdaRdIdeType::class
        override val id: RdId get() = RdId(-3520458110972391976)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LambdaRdIdeType {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LambdaRdIdeType)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * #### Generated from [LambdaTestModel.kt]
 */
data class LambdaRdTestActionParameters (
    val reference: String,
    val parameters: List<String>?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<LambdaRdTestActionParameters> {
        override val _type: KClass<LambdaRdTestActionParameters> = LambdaRdTestActionParameters::class
        override val id: RdId get() = RdId(-3702464714964495074)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LambdaRdTestActionParameters  {
            val reference = buffer.readString()
            val parameters = buffer.readNullable { buffer.readList { buffer.readString() } }
            return LambdaRdTestActionParameters(reference, parameters)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LambdaRdTestActionParameters)  {
            buffer.writeString(value.reference)
            buffer.writeNullable(value.parameters) { buffer.writeList(it) { v -> buffer.writeString(v) } }
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
        
        other as LambdaRdTestActionParameters
        
        if (reference != other.reference) return false
        if (parameters != other.parameters) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + reference.hashCode()
        __r = __r*31 + if (parameters != null) parameters.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LambdaRdTestActionParameters (")
        printer.indent {
            print("reference = "); reference.print(printer); println()
            print("parameters = "); parameters.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [LambdaTestModel.kt]
 */
class LambdaRdTestSession private constructor(
    val rdIdeInfo: LambdaRdIdeInfo,
    private val _ready: RdProperty<Boolean?>,
    private val _sendException: RdSignal<LambdaRdTestSessionException>,
    private val _closeAllOpenedProjects: RdCall<Unit, Boolean>,
    private val _runLambda: RdCall<LambdaRdTestActionParameters, Unit>,
    private val _requestFocus: RdCall<Boolean, Boolean>,
    private val _isFocused: RdCall<Unit, Boolean>,
    private val _visibleFrameNames: RdCall<Unit, List<String>>,
    private val _projectsNames: RdCall<Unit, List<String>>,
    private val _makeScreenshot: RdCall<String, Boolean>,
    private val _isResponding: RdCall<Unit, Boolean>,
    private val _projectsAreInitialised: RdCall<Unit, Boolean>
) : RdBindableBase() {
    //companion
    
    companion object : IMarshaller<LambdaRdTestSession> {
        override val _type: KClass<LambdaRdTestSession> = LambdaRdTestSession::class
        override val id: RdId get() = RdId(3210199037986225272)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LambdaRdTestSession  {
            val _id = RdId.read(buffer)
            val rdIdeInfo = LambdaRdIdeInfo.read(ctx, buffer)
            val _ready = RdProperty.read(ctx, buffer, __BoolNullableSerializer)
            val _sendException = RdSignal.read(ctx, buffer, LambdaRdTestSessionException)
            val _closeAllOpenedProjects = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            val _runLambda = RdCall.read(ctx, buffer, LambdaRdTestActionParameters, FrameworkMarshallers.Void)
            val _requestFocus = RdCall.read(ctx, buffer, FrameworkMarshallers.Bool, FrameworkMarshallers.Bool)
            val _isFocused = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            val _visibleFrameNames = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, __StringListSerializer)
            val _projectsNames = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, __StringListSerializer)
            val _makeScreenshot = RdCall.read(ctx, buffer, FrameworkMarshallers.String, FrameworkMarshallers.Bool)
            val _isResponding = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            val _projectsAreInitialised = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            return LambdaRdTestSession(rdIdeInfo, _ready, _sendException, _closeAllOpenedProjects, _runLambda, _requestFocus, _isFocused, _visibleFrameNames, _projectsNames, _makeScreenshot, _isResponding, _projectsAreInitialised).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LambdaRdTestSession)  {
            value.rdid.write(buffer)
            LambdaRdIdeInfo.write(ctx, buffer, value.rdIdeInfo)
            RdProperty.write(ctx, buffer, value._ready)
            RdSignal.write(ctx, buffer, value._sendException)
            RdCall.write(ctx, buffer, value._closeAllOpenedProjects)
            RdCall.write(ctx, buffer, value._runLambda)
            RdCall.write(ctx, buffer, value._requestFocus)
            RdCall.write(ctx, buffer, value._isFocused)
            RdCall.write(ctx, buffer, value._visibleFrameNames)
            RdCall.write(ctx, buffer, value._projectsNames)
            RdCall.write(ctx, buffer, value._makeScreenshot)
            RdCall.write(ctx, buffer, value._isResponding)
            RdCall.write(ctx, buffer, value._projectsAreInitialised)
        }
        
        private val __BoolNullableSerializer = FrameworkMarshallers.Bool.nullable()
        private val __StringListSerializer = FrameworkMarshallers.String.list()
        
    }
    //fields
    val ready: IProperty<Boolean?> get() = _ready
    val sendException: IAsyncSignal<LambdaRdTestSessionException> get() = _sendException
    val closeAllOpenedProjects: RdCall<Unit, Boolean> get() = _closeAllOpenedProjects
    val runLambda: RdCall<LambdaRdTestActionParameters, Unit> get() = _runLambda
    val requestFocus: RdCall<Boolean, Boolean> get() = _requestFocus
    val isFocused: RdCall<Unit, Boolean> get() = _isFocused
    val visibleFrameNames: RdCall<Unit, List<String>> get() = _visibleFrameNames
    val projectsNames: RdCall<Unit, List<String>> get() = _projectsNames
    val makeScreenshot: RdCall<String, Boolean> get() = _makeScreenshot
    val isResponding: RdCall<Unit, Boolean> get() = _isResponding
    val projectsAreInitialised: RdCall<Unit, Boolean> get() = _projectsAreInitialised
    //methods
    //initializer
    init {
        _ready.optimizeNested = true
    }
    
    init {
        _sendException.async = true
        _closeAllOpenedProjects.async = true
        _runLambda.async = true
        _requestFocus.async = true
        _isFocused.async = true
        _visibleFrameNames.async = true
        _projectsNames.async = true
        _makeScreenshot.async = true
        _isResponding.async = true
        _projectsAreInitialised.async = true
    }
    
    init {
        bindableChildren.add("ready" to _ready)
        bindableChildren.add("sendException" to _sendException)
        bindableChildren.add("closeAllOpenedProjects" to _closeAllOpenedProjects)
        bindableChildren.add("runLambda" to _runLambda)
        bindableChildren.add("requestFocus" to _requestFocus)
        bindableChildren.add("isFocused" to _isFocused)
        bindableChildren.add("visibleFrameNames" to _visibleFrameNames)
        bindableChildren.add("projectsNames" to _projectsNames)
        bindableChildren.add("makeScreenshot" to _makeScreenshot)
        bindableChildren.add("isResponding" to _isResponding)
        bindableChildren.add("projectsAreInitialised" to _projectsAreInitialised)
    }
    
    //secondary constructor
    constructor(
        rdIdeInfo: LambdaRdIdeInfo
    ) : this(
        rdIdeInfo,
        RdProperty<Boolean?>(null, __BoolNullableSerializer),
        RdSignal<LambdaRdTestSessionException>(LambdaRdTestSessionException),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool),
        RdCall<LambdaRdTestActionParameters, Unit>(LambdaRdTestActionParameters, FrameworkMarshallers.Void),
        RdCall<Boolean, Boolean>(FrameworkMarshallers.Bool, FrameworkMarshallers.Bool),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool),
        RdCall<Unit, List<String>>(FrameworkMarshallers.Void, __StringListSerializer),
        RdCall<Unit, List<String>>(FrameworkMarshallers.Void, __StringListSerializer),
        RdCall<String, Boolean>(FrameworkMarshallers.String, FrameworkMarshallers.Bool),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LambdaRdTestSession (")
        printer.indent {
            print("rdIdeInfo = "); rdIdeInfo.print(printer); println()
            print("ready = "); _ready.print(printer); println()
            print("sendException = "); _sendException.print(printer); println()
            print("closeAllOpenedProjects = "); _closeAllOpenedProjects.print(printer); println()
            print("runLambda = "); _runLambda.print(printer); println()
            print("requestFocus = "); _requestFocus.print(printer); println()
            print("isFocused = "); _isFocused.print(printer); println()
            print("visibleFrameNames = "); _visibleFrameNames.print(printer); println()
            print("projectsNames = "); _projectsNames.print(printer); println()
            print("makeScreenshot = "); _makeScreenshot.print(printer); println()
            print("isResponding = "); _isResponding.print(printer); println()
            print("projectsAreInitialised = "); _projectsAreInitialised.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): LambdaRdTestSession   {
        return LambdaRdTestSession(
            rdIdeInfo,
            _ready.deepClonePolymorphic(),
            _sendException.deepClonePolymorphic(),
            _closeAllOpenedProjects.deepClonePolymorphic(),
            _runLambda.deepClonePolymorphic(),
            _requestFocus.deepClonePolymorphic(),
            _isFocused.deepClonePolymorphic(),
            _visibleFrameNames.deepClonePolymorphic(),
            _projectsNames.deepClonePolymorphic(),
            _makeScreenshot.deepClonePolymorphic(),
            _isResponding.deepClonePolymorphic(),
            _projectsAreInitialised.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
}


/**
 * #### Generated from [LambdaTestModel.kt]
 */
data class LambdaRdTestSessionException (
    val type: String,
    val originalType: String?,
    val message: String?,
    val stacktrace: List<LambdaRdTestSessionStackTraceElement>,
    val cause: LambdaRdTestSessionExceptionCause?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<LambdaRdTestSessionException> {
        override val _type: KClass<LambdaRdTestSessionException> = LambdaRdTestSessionException::class
        override val id: RdId get() = RdId(-1075846985405547849)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LambdaRdTestSessionException  {
            val type = buffer.readString()
            val originalType = buffer.readNullable { buffer.readString() }
            val message = buffer.readNullable { buffer.readString() }
            val stacktrace = buffer.readList { LambdaRdTestSessionStackTraceElement.read(ctx, buffer) }
            val cause = buffer.readNullable { LambdaRdTestSessionExceptionCause.read(ctx, buffer) }
            return LambdaRdTestSessionException(type, originalType, message, stacktrace, cause)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LambdaRdTestSessionException)  {
            buffer.writeString(value.type)
            buffer.writeNullable(value.originalType) { buffer.writeString(it) }
            buffer.writeNullable(value.message) { buffer.writeString(it) }
            buffer.writeList(value.stacktrace) { v -> LambdaRdTestSessionStackTraceElement.write(ctx, buffer, v) }
            buffer.writeNullable(value.cause) { LambdaRdTestSessionExceptionCause.write(ctx, buffer, it) }
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
        
        other as LambdaRdTestSessionException
        
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
        printer.println("LambdaRdTestSessionException (")
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
 * #### Generated from [LambdaTestModel.kt]
 */
data class LambdaRdTestSessionExceptionCause (
    val type: String,
    val message: String?,
    val stacktrace: List<LambdaRdTestSessionStackTraceElement>
) : IPrintable {
    //companion
    
    companion object : IMarshaller<LambdaRdTestSessionExceptionCause> {
        override val _type: KClass<LambdaRdTestSessionExceptionCause> = LambdaRdTestSessionExceptionCause::class
        override val id: RdId get() = RdId(-1877965166079974414)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LambdaRdTestSessionExceptionCause  {
            val type = buffer.readString()
            val message = buffer.readNullable { buffer.readString() }
            val stacktrace = buffer.readList { LambdaRdTestSessionStackTraceElement.read(ctx, buffer) }
            return LambdaRdTestSessionExceptionCause(type, message, stacktrace)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LambdaRdTestSessionExceptionCause)  {
            buffer.writeString(value.type)
            buffer.writeNullable(value.message) { buffer.writeString(it) }
            buffer.writeList(value.stacktrace) { v -> LambdaRdTestSessionStackTraceElement.write(ctx, buffer, v) }
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
        
        other as LambdaRdTestSessionExceptionCause
        
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
        printer.println("LambdaRdTestSessionExceptionCause (")
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
 * #### Generated from [LambdaTestModel.kt]
 */
data class LambdaRdTestSessionStackTraceElement (
    val declaringClass: String,
    val methodName: String,
    val fileName: String,
    val lineNumber: Int
) : IPrintable {
    //companion
    
    companion object : IMarshaller<LambdaRdTestSessionStackTraceElement> {
        override val _type: KClass<LambdaRdTestSessionStackTraceElement> = LambdaRdTestSessionStackTraceElement::class
        override val id: RdId get() = RdId(-8183511780297815289)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LambdaRdTestSessionStackTraceElement  {
            val declaringClass = buffer.readString()
            val methodName = buffer.readString()
            val fileName = buffer.readString()
            val lineNumber = buffer.readInt()
            return LambdaRdTestSessionStackTraceElement(declaringClass, methodName, fileName, lineNumber)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LambdaRdTestSessionStackTraceElement)  {
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
        
        other as LambdaRdTestSessionStackTraceElement
        
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
        printer.println("LambdaRdTestSessionStackTraceElement (")
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
