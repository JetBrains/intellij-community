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
            serializers.register(LazyCompanionMarshaller(RdId(-3520458110972391976), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType"))
            serializers.register(LazyCompanionMarshaller(RdId(-8183511780297815289), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSessionStackTraceElement"))
            serializers.register(LazyCompanionMarshaller(RdId(-1877965166079974414), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSessionExceptionCause"))
            serializers.register(LazyCompanionMarshaller(RdId(-1075846985405547849), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSessionException"))
            serializers.register(LazyCompanionMarshaller(RdId(-3702464714964495074), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestActionParameters"))
            serializers.register(LazyCompanionMarshaller(RdId(-8227259315004769232), classLoader, "com.intellij.remoteDev.tests.modelGenerated.LambdaRdSerialized"))
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
        
        const val serializationHash = 7766307017172391987L
        
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
data class LambdaRdSerialized (
    val stepName: String,
    val serializedDataBase64: String,
    val classPath: List<String>,
    val parametersBase64: List<String>,
    val globalTestScope: Boolean
) : IPrintable {
    //companion
    
    companion object : IMarshaller<LambdaRdSerialized> {
        override val _type: KClass<LambdaRdSerialized> = LambdaRdSerialized::class
        override val id: RdId get() = RdId(-8227259315004769232)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LambdaRdSerialized  {
            val stepName = buffer.readString()
            val serializedDataBase64 = buffer.readString()
            val classPath = buffer.readList { buffer.readString() }
            val parametersBase64 = buffer.readList { buffer.readString() }
            val globalTestScope = buffer.readBool()
            return LambdaRdSerialized(stepName, serializedDataBase64, classPath, parametersBase64, globalTestScope)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LambdaRdSerialized)  {
            buffer.writeString(value.stepName)
            buffer.writeString(value.serializedDataBase64)
            buffer.writeList(value.classPath) { v -> buffer.writeString(v) }
            buffer.writeList(value.parametersBase64) { v -> buffer.writeString(v) }
            buffer.writeBool(value.globalTestScope)
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
        
        other as LambdaRdSerialized
        
        if (stepName != other.stepName) return false
        if (serializedDataBase64 != other.serializedDataBase64) return false
        if (classPath != other.classPath) return false
        if (parametersBase64 != other.parametersBase64) return false
        if (globalTestScope != other.globalTestScope) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + stepName.hashCode()
        __r = __r*31 + serializedDataBase64.hashCode()
        __r = __r*31 + classPath.hashCode()
        __r = __r*31 + parametersBase64.hashCode()
        __r = __r*31 + globalTestScope.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LambdaRdSerialized (")
        printer.indent {
            print("stepName = "); stepName.print(printer); println()
            print("serializedDataBase64 = "); serializedDataBase64.print(printer); println()
            print("classPath = "); classPath.print(printer); println()
            print("parametersBase64 = "); parametersBase64.print(printer); println()
            print("globalTestScope = "); globalTestScope.print(printer); println()
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
data class LambdaRdTestActionParameters (
    val reference: String,
    val testClass: String,
    val testMethod: String,
    val methodArgumentssBase64: List<String>
) : IPrintable {
    //companion
    
    companion object : IMarshaller<LambdaRdTestActionParameters> {
        override val _type: KClass<LambdaRdTestActionParameters> = LambdaRdTestActionParameters::class
        override val id: RdId get() = RdId(-3702464714964495074)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LambdaRdTestActionParameters  {
            val reference = buffer.readString()
            val testClass = buffer.readString()
            val testMethod = buffer.readString()
            val methodArgumentssBase64 = buffer.readList { buffer.readString() }
            return LambdaRdTestActionParameters(reference, testClass, testMethod, methodArgumentssBase64)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LambdaRdTestActionParameters)  {
            buffer.writeString(value.reference)
            buffer.writeString(value.testClass)
            buffer.writeString(value.testMethod)
            buffer.writeList(value.methodArgumentssBase64) { v -> buffer.writeString(v) }
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
        if (testClass != other.testClass) return false
        if (testMethod != other.testMethod) return false
        if (methodArgumentssBase64 != other.methodArgumentssBase64) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + reference.hashCode()
        __r = __r*31 + testClass.hashCode()
        __r = __r*31 + testMethod.hashCode()
        __r = __r*31 + methodArgumentssBase64.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LambdaRdTestActionParameters (")
        printer.indent {
            print("reference = "); reference.print(printer); println()
            print("testClass = "); testClass.print(printer); println()
            print("testMethod = "); testMethod.print(printer); println()
            print("methodArgumentssBase64 = "); methodArgumentssBase64.print(printer); println()
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
    val rdIdeType: LambdaRdIdeType,
    private val _ready: RdProperty<Boolean?>,
    private val _sendException: RdSignal<LambdaRdTestSessionException>,
    private val _runLambda: RdCall<LambdaRdTestActionParameters, Unit>,
    private val _runSerializedLambda: RdCall<LambdaRdSerialized, String>,
    private val _beforeEach: RdCall<String, Unit>,
    private val _beforeAll: RdCall<String, Unit>,
    private val _afterEach: RdCall<String, Unit>,
    private val _afterAll: RdCall<String, Unit>,
    private val _isResponding: RdCall<Unit, Boolean>
) : RdBindableBase() {
    //companion
    
    companion object : IMarshaller<LambdaRdTestSession> {
        override val _type: KClass<LambdaRdTestSession> = LambdaRdTestSession::class
        override val id: RdId get() = RdId(3210199037986225272)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LambdaRdTestSession  {
            val _id = RdId.read(buffer)
            val rdIdeType = buffer.readEnum<LambdaRdIdeType>()
            val _ready = RdProperty.read(ctx, buffer, __BoolNullableSerializer)
            val _sendException = RdSignal.read(ctx, buffer, LambdaRdTestSessionException)
            val _runLambda = RdCall.read(ctx, buffer, LambdaRdTestActionParameters, FrameworkMarshallers.Void)
            val _runSerializedLambda = RdCall.read(ctx, buffer, LambdaRdSerialized, FrameworkMarshallers.String)
            val _beforeEach = RdCall.read(ctx, buffer, FrameworkMarshallers.String, FrameworkMarshallers.Void)
            val _beforeAll = RdCall.read(ctx, buffer, FrameworkMarshallers.String, FrameworkMarshallers.Void)
            val _afterEach = RdCall.read(ctx, buffer, FrameworkMarshallers.String, FrameworkMarshallers.Void)
            val _afterAll = RdCall.read(ctx, buffer, FrameworkMarshallers.String, FrameworkMarshallers.Void)
            val _isResponding = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            return LambdaRdTestSession(rdIdeType, _ready, _sendException, _runLambda, _runSerializedLambda, _beforeEach, _beforeAll, _afterEach, _afterAll, _isResponding).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LambdaRdTestSession)  {
            value.rdid.write(buffer)
            buffer.writeEnum(value.rdIdeType)
            RdProperty.write(ctx, buffer, value._ready)
            RdSignal.write(ctx, buffer, value._sendException)
            RdCall.write(ctx, buffer, value._runLambda)
            RdCall.write(ctx, buffer, value._runSerializedLambda)
            RdCall.write(ctx, buffer, value._beforeEach)
            RdCall.write(ctx, buffer, value._beforeAll)
            RdCall.write(ctx, buffer, value._afterEach)
            RdCall.write(ctx, buffer, value._afterAll)
            RdCall.write(ctx, buffer, value._isResponding)
        }
        
        private val __BoolNullableSerializer = FrameworkMarshallers.Bool.nullable()
        
    }
    //fields
    val ready: IProperty<Boolean?> get() = _ready
    val sendException: IAsyncSignal<LambdaRdTestSessionException> get() = _sendException
    val runLambda: RdCall<LambdaRdTestActionParameters, Unit> get() = _runLambda
    val runSerializedLambda: RdCall<LambdaRdSerialized, String> get() = _runSerializedLambda
    val beforeEach: RdCall<String, Unit> get() = _beforeEach
    val beforeAll: RdCall<String, Unit> get() = _beforeAll
    val afterEach: RdCall<String, Unit> get() = _afterEach
    val afterAll: RdCall<String, Unit> get() = _afterAll
    val isResponding: RdCall<Unit, Boolean> get() = _isResponding
    //methods
    //initializer
    init {
        _ready.optimizeNested = true
    }
    
    init {
        _sendException.async = true
        _runLambda.async = true
        _runSerializedLambda.async = true
        _beforeEach.async = true
        _beforeAll.async = true
        _afterEach.async = true
        _afterAll.async = true
        _isResponding.async = true
    }
    
    init {
        bindableChildren.add("ready" to _ready)
        bindableChildren.add("sendException" to _sendException)
        bindableChildren.add("runLambda" to _runLambda)
        bindableChildren.add("runSerializedLambda" to _runSerializedLambda)
        bindableChildren.add("beforeEach" to _beforeEach)
        bindableChildren.add("beforeAll" to _beforeAll)
        bindableChildren.add("afterEach" to _afterEach)
        bindableChildren.add("afterAll" to _afterAll)
        bindableChildren.add("isResponding" to _isResponding)
    }
    
    //secondary constructor
    constructor(
        rdIdeType: LambdaRdIdeType
    ) : this(
        rdIdeType,
        RdProperty<Boolean?>(null, __BoolNullableSerializer),
        RdSignal<LambdaRdTestSessionException>(LambdaRdTestSessionException),
        RdCall<LambdaRdTestActionParameters, Unit>(LambdaRdTestActionParameters, FrameworkMarshallers.Void),
        RdCall<LambdaRdSerialized, String>(LambdaRdSerialized, FrameworkMarshallers.String),
        RdCall<String, Unit>(FrameworkMarshallers.String, FrameworkMarshallers.Void),
        RdCall<String, Unit>(FrameworkMarshallers.String, FrameworkMarshallers.Void),
        RdCall<String, Unit>(FrameworkMarshallers.String, FrameworkMarshallers.Void),
        RdCall<String, Unit>(FrameworkMarshallers.String, FrameworkMarshallers.Void),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LambdaRdTestSession (")
        printer.indent {
            print("rdIdeType = "); rdIdeType.print(printer); println()
            print("ready = "); _ready.print(printer); println()
            print("sendException = "); _sendException.print(printer); println()
            print("runLambda = "); _runLambda.print(printer); println()
            print("runSerializedLambda = "); _runSerializedLambda.print(printer); println()
            print("beforeEach = "); _beforeEach.print(printer); println()
            print("beforeAll = "); _beforeAll.print(printer); println()
            print("afterEach = "); _afterEach.print(printer); println()
            print("afterAll = "); _afterAll.print(printer); println()
            print("isResponding = "); _isResponding.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): LambdaRdTestSession   {
        return LambdaRdTestSession(
            rdIdeType,
            _ready.deepClonePolymorphic(),
            _sendException.deepClonePolymorphic(),
            _runLambda.deepClonePolymorphic(),
            _runSerializedLambda.deepClonePolymorphic(),
            _beforeEach.deepClonePolymorphic(),
            _beforeAll.deepClonePolymorphic(),
            _afterEach.deepClonePolymorphic(),
            _afterAll.deepClonePolymorphic(),
            _isResponding.deepClonePolymorphic()
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
