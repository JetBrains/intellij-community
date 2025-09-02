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
            val classLoader = javaClass.classLoader
            serializers.register(LazyCompanionMarshaller(RdId(552672907393362222), classLoader, "com.intellij.remoteDev.tests.modelGenerated.RdAgentInfo"))
            serializers.register(LazyCompanionMarshaller(RdId(552672907393700794), classLoader, "com.intellij.remoteDev.tests.modelGenerated.RdAgentType"))
            serializers.register(LazyCompanionMarshaller(RdId(-3824320616986309148), classLoader, "com.intellij.remoteDev.tests.modelGenerated.RdProductType"))
            serializers.register(LazyCompanionMarshaller(RdId(-4029698853809470560), classLoader, "com.intellij.remoteDev.tests.modelGenerated.RdTestSessionStackTraceElement"))
            serializers.register(LazyCompanionMarshaller(RdId(-2964405344154034056), classLoader, "com.intellij.remoteDev.tests.modelGenerated.RdTestSessionLightException"))
            serializers.register(LazyCompanionMarshaller(RdId(-6820612235039581104), classLoader, "com.intellij.remoteDev.tests.modelGenerated.RdTestSessionException"))
            serializers.register(LazyCompanionMarshaller(RdId(8999514109111023287), classLoader, "com.intellij.remoteDev.tests.modelGenerated.RdTestActionParameters"))
            serializers.register(LazyCompanionMarshaller(RdId(1797576418817339312), classLoader, "com.intellij.remoteDev.tests.modelGenerated.RdTestComponentData"))
            serializers.register(LazyCompanionMarshaller(RdId(-3821381997278381377), classLoader, "com.intellij.remoteDev.tests.modelGenerated.RdTestSession"))
            serializers.register(LazyCompanionMarshaller(RdId(-3824320616986647720), classLoader, "com.intellij.remoteDev.tests.modelGenerated.RdProductInfo"))
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
        
        const val serializationHash = 4116578669314861084L
        
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
    val productType: RdProductType,
    val testIdeProductCode: String,
    val testQualifiedClassName: String,
    val testMethodNonParameterizedName: String,
    val testMethodParametersArrayString: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdAgentInfo> {
        override val _type: KClass<RdAgentInfo> = RdAgentInfo::class
        override val id: RdId get() = RdId(552672907393362222)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdAgentInfo  {
            val id = buffer.readString()
            val launchNumber = buffer.readInt()
            val agentType = buffer.readEnum<RdAgentType>()
            val productType = buffer.readEnum<RdProductType>()
            val testIdeProductCode = buffer.readString()
            val testQualifiedClassName = buffer.readString()
            val testMethodNonParameterizedName = buffer.readString()
            val testMethodParametersArrayString = buffer.readString()
            return RdAgentInfo(id, launchNumber, agentType, productType, testIdeProductCode, testQualifiedClassName, testMethodNonParameterizedName, testMethodParametersArrayString)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdAgentInfo)  {
            buffer.writeString(value.id)
            buffer.writeInt(value.launchNumber)
            buffer.writeEnum(value.agentType)
            buffer.writeEnum(value.productType)
            buffer.writeString(value.testIdeProductCode)
            buffer.writeString(value.testQualifiedClassName)
            buffer.writeString(value.testMethodNonParameterizedName)
            buffer.writeString(value.testMethodParametersArrayString)
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
        if (testIdeProductCode != other.testIdeProductCode) return false
        if (testQualifiedClassName != other.testQualifiedClassName) return false
        if (testMethodNonParameterizedName != other.testMethodNonParameterizedName) return false
        if (testMethodParametersArrayString != other.testMethodParametersArrayString) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + launchNumber.hashCode()
        __r = __r*31 + agentType.hashCode()
        __r = __r*31 + productType.hashCode()
        __r = __r*31 + testIdeProductCode.hashCode()
        __r = __r*31 + testQualifiedClassName.hashCode()
        __r = __r*31 + testMethodNonParameterizedName.hashCode()
        __r = __r*31 + testMethodParametersArrayString.hashCode()
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
            print("testIdeProductCode = "); testIdeProductCode.print(printer); println()
            print("testQualifiedClassName = "); testQualifiedClassName.print(printer); println()
            print("testMethodNonParameterizedName = "); testMethodNonParameterizedName.print(printer); println()
            print("testMethodParametersArrayString = "); testMethodParametersArrayString.print(printer); println()
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
    
    companion object : IMarshaller<RdAgentType> {
        val marshaller = FrameworkMarshallers.enum<RdAgentType>()
        
        
        override val _type: KClass<RdAgentType> = RdAgentType::class
        override val id: RdId get() = RdId(552672907393700794)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdAgentType {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdAgentType)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * #### Generated from [DistributedTestModel.kt]
 */
data class RdProductInfo (
    val productCode: String,
    val productVersion: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdProductInfo> {
        override val _type: KClass<RdProductInfo> = RdProductInfo::class
        override val id: RdId get() = RdId(-3824320616986647720)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdProductInfo  {
            val productCode = buffer.readString()
            val productVersion = buffer.readString()
            return RdProductInfo(productCode, productVersion)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdProductInfo)  {
            buffer.writeString(value.productCode)
            buffer.writeString(value.productVersion)
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
        
        other as RdProductInfo
        
        if (productCode != other.productCode) return false
        if (productVersion != other.productVersion) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + productCode.hashCode()
        __r = __r*31 + productVersion.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdProductInfo (")
        printer.indent {
            print("productCode = "); productCode.print(printer); println()
            print("productVersion = "); productVersion.print(printer); println()
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
enum class RdProductType {
    REMOTE_DEVELOPMENT, 
    CODE_WITH_ME;
    
    companion object : IMarshaller<RdProductType> {
        val marshaller = FrameworkMarshallers.enum<RdProductType>()
        
        
        override val _type: KClass<RdProductType> = RdProductType::class
        override val id: RdId get() = RdId(-3824320616986309148)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdProductType {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdProductType)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * #### Generated from [DistributedTestModel.kt]
 */
data class RdTestActionParameters (
    val title: String,
    val parameters: List<String>?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdTestActionParameters> {
        override val _type: KClass<RdTestActionParameters> = RdTestActionParameters::class
        override val id: RdId get() = RdId(8999514109111023287)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdTestActionParameters  {
            val title = buffer.readString()
            val parameters = buffer.readNullable { buffer.readList { buffer.readString() } }
            return RdTestActionParameters(title, parameters)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestActionParameters)  {
            buffer.writeString(value.title)
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
        
        other as RdTestActionParameters
        
        if (title != other.title) return false
        if (parameters != other.parameters) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + title.hashCode()
        __r = __r*31 + if (parameters != null) parameters.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdTestActionParameters (")
        printer.indent {
            print("title = "); title.print(printer); println()
            print("parameters = "); parameters.print(printer); println()
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
data class RdTestComponentData (
    val width: Int,
    val height: Int
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdTestComponentData> {
        override val _type: KClass<RdTestComponentData> = RdTestComponentData::class
        override val id: RdId get() = RdId(1797576418817339312)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdTestComponentData  {
            val width = buffer.readInt()
            val height = buffer.readInt()
            return RdTestComponentData(width, height)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestComponentData)  {
            buffer.writeInt(value.width)
            buffer.writeInt(value.height)
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
        
        other as RdTestComponentData
        
        if (width != other.width) return false
        if (height != other.height) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + width.hashCode()
        __r = __r*31 + height.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdTestComponentData (")
        printer.indent {
            print("width = "); width.print(printer); println()
            print("height = "); height.print(printer); println()
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
class RdTestSession private constructor(
    val rdAgentInfo: RdAgentInfo,
    val runTestMethod: Boolean,
    val traceCategories: List<String>,
    val debugCategories: List<String>,
    private val _ready: RdProperty<Boolean?>,
    private val _sendException: RdSignal<RdTestSessionException>,
    private val _exitApp: RdSignal<Unit>,
    private val _showNotification: RdSignal<String>,
    private val _forceLeaveAllModals: RdCall<Boolean, Unit>,
    private val _closeAllOpenedProjects: RdCall<Unit, Boolean>,
    private val _runNextAction: RdCall<RdTestActionParameters, String?>,
    private val _requestFocus: RdCall<Boolean, Boolean>,
    private val _isFocused: RdCall<Unit, Boolean>,
    private val _visibleFrameNames: RdCall<Unit, List<String>>,
    private val _projectsNames: RdCall<Unit, List<String>>,
    private val _makeScreenshot: RdCall<String, Boolean>,
    private val _isResponding: RdCall<Unit, Boolean>,
    private val _projectsAreInitialised: RdCall<Unit, Boolean>,
    private val _getProductCodeAndVersion: RdCall<Unit, RdProductInfo>
) : RdBindableBase() {
    //companion
    
    companion object : IMarshaller<RdTestSession> {
        override val _type: KClass<RdTestSession> = RdTestSession::class
        override val id: RdId get() = RdId(-3821381997278381377)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdTestSession  {
            val _id = RdId.read(buffer)
            val rdAgentInfo = RdAgentInfo.read(ctx, buffer)
            val runTestMethod = buffer.readBool()
            val traceCategories = buffer.readList { buffer.readString() }
            val debugCategories = buffer.readList { buffer.readString() }
            val _ready = RdProperty.read(ctx, buffer, __BoolNullableSerializer)
            val _sendException = RdSignal.read(ctx, buffer, RdTestSessionException)
            val _exitApp = RdSignal.read(ctx, buffer, FrameworkMarshallers.Void)
            val _showNotification = RdSignal.read(ctx, buffer, FrameworkMarshallers.String)
            val _forceLeaveAllModals = RdCall.read(ctx, buffer, FrameworkMarshallers.Bool, FrameworkMarshallers.Void)
            val _closeAllOpenedProjects = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            val _runNextAction = RdCall.read(ctx, buffer, RdTestActionParameters, __StringNullableSerializer)
            val _requestFocus = RdCall.read(ctx, buffer, FrameworkMarshallers.Bool, FrameworkMarshallers.Bool)
            val _isFocused = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            val _visibleFrameNames = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, __StringListSerializer)
            val _projectsNames = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, __StringListSerializer)
            val _makeScreenshot = RdCall.read(ctx, buffer, FrameworkMarshallers.String, FrameworkMarshallers.Bool)
            val _isResponding = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            val _projectsAreInitialised = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, FrameworkMarshallers.Bool)
            val _getProductCodeAndVersion = RdCall.read(ctx, buffer, FrameworkMarshallers.Void, RdProductInfo)
            return RdTestSession(rdAgentInfo, runTestMethod, traceCategories, debugCategories, _ready, _sendException, _exitApp, _showNotification, _forceLeaveAllModals, _closeAllOpenedProjects, _runNextAction, _requestFocus, _isFocused, _visibleFrameNames, _projectsNames, _makeScreenshot, _isResponding, _projectsAreInitialised, _getProductCodeAndVersion).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestSession)  {
            value.rdid.write(buffer)
            RdAgentInfo.write(ctx, buffer, value.rdAgentInfo)
            buffer.writeBool(value.runTestMethod)
            buffer.writeList(value.traceCategories) { v -> buffer.writeString(v) }
            buffer.writeList(value.debugCategories) { v -> buffer.writeString(v) }
            RdProperty.write(ctx, buffer, value._ready)
            RdSignal.write(ctx, buffer, value._sendException)
            RdSignal.write(ctx, buffer, value._exitApp)
            RdSignal.write(ctx, buffer, value._showNotification)
            RdCall.write(ctx, buffer, value._forceLeaveAllModals)
            RdCall.write(ctx, buffer, value._closeAllOpenedProjects)
            RdCall.write(ctx, buffer, value._runNextAction)
            RdCall.write(ctx, buffer, value._requestFocus)
            RdCall.write(ctx, buffer, value._isFocused)
            RdCall.write(ctx, buffer, value._visibleFrameNames)
            RdCall.write(ctx, buffer, value._projectsNames)
            RdCall.write(ctx, buffer, value._makeScreenshot)
            RdCall.write(ctx, buffer, value._isResponding)
            RdCall.write(ctx, buffer, value._projectsAreInitialised)
            RdCall.write(ctx, buffer, value._getProductCodeAndVersion)
        }
        
        private val __BoolNullableSerializer = FrameworkMarshallers.Bool.nullable()
        private val __StringNullableSerializer = FrameworkMarshallers.String.nullable()
        private val __StringListSerializer = FrameworkMarshallers.String.list()
        
    }
    //fields
    val ready: IProperty<Boolean?> get() = _ready
    val sendException: IAsyncSignal<RdTestSessionException> get() = _sendException
    val exitApp: IAsyncSignal<Unit> get() = _exitApp
    val showNotification: ISignal<String> get() = _showNotification
    val forceLeaveAllModals: RdCall<Boolean, Unit> get() = _forceLeaveAllModals
    val closeAllOpenedProjects: RdCall<Unit, Boolean> get() = _closeAllOpenedProjects
    val runNextAction: RdCall<RdTestActionParameters, String?> get() = _runNextAction
    val requestFocus: RdCall<Boolean, Boolean> get() = _requestFocus
    val isFocused: RdCall<Unit, Boolean> get() = _isFocused
    val visibleFrameNames: RdCall<Unit, List<String>> get() = _visibleFrameNames
    val projectsNames: RdCall<Unit, List<String>> get() = _projectsNames
    val makeScreenshot: RdCall<String, Boolean> get() = _makeScreenshot
    val isResponding: RdCall<Unit, Boolean> get() = _isResponding
    val projectsAreInitialised: RdCall<Unit, Boolean> get() = _projectsAreInitialised
    val getProductCodeAndVersion: RdCall<Unit, RdProductInfo> get() = _getProductCodeAndVersion
    //methods
    //initializer
    init {
        _ready.optimizeNested = true
    }
    
    init {
        _sendException.async = true
        _exitApp.async = true
        _forceLeaveAllModals.async = true
        _closeAllOpenedProjects.async = true
        _runNextAction.async = true
        _requestFocus.async = true
        _isFocused.async = true
        _visibleFrameNames.async = true
        _projectsNames.async = true
        _makeScreenshot.async = true
        _isResponding.async = true
        _projectsAreInitialised.async = true
        _getProductCodeAndVersion.async = true
    }
    
    init {
        bindableChildren.add("ready" to _ready)
        bindableChildren.add("sendException" to _sendException)
        bindableChildren.add("exitApp" to _exitApp)
        bindableChildren.add("showNotification" to _showNotification)
        bindableChildren.add("forceLeaveAllModals" to _forceLeaveAllModals)
        bindableChildren.add("closeAllOpenedProjects" to _closeAllOpenedProjects)
        bindableChildren.add("runNextAction" to _runNextAction)
        bindableChildren.add("requestFocus" to _requestFocus)
        bindableChildren.add("isFocused" to _isFocused)
        bindableChildren.add("visibleFrameNames" to _visibleFrameNames)
        bindableChildren.add("projectsNames" to _projectsNames)
        bindableChildren.add("makeScreenshot" to _makeScreenshot)
        bindableChildren.add("isResponding" to _isResponding)
        bindableChildren.add("projectsAreInitialised" to _projectsAreInitialised)
        bindableChildren.add("getProductCodeAndVersion" to _getProductCodeAndVersion)
    }
    
    //secondary constructor
    constructor(
        rdAgentInfo: RdAgentInfo,
        runTestMethod: Boolean,
        traceCategories: List<String>,
        debugCategories: List<String>
    ) : this(
        rdAgentInfo,
        runTestMethod,
        traceCategories,
        debugCategories,
        RdProperty<Boolean?>(null, __BoolNullableSerializer),
        RdSignal<RdTestSessionException>(RdTestSessionException),
        RdSignal<Unit>(FrameworkMarshallers.Void),
        RdSignal<String>(FrameworkMarshallers.String),
        RdCall<Boolean, Unit>(FrameworkMarshallers.Bool, FrameworkMarshallers.Void),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool),
        RdCall<RdTestActionParameters, String?>(RdTestActionParameters, __StringNullableSerializer),
        RdCall<Boolean, Boolean>(FrameworkMarshallers.Bool, FrameworkMarshallers.Bool),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool),
        RdCall<Unit, List<String>>(FrameworkMarshallers.Void, __StringListSerializer),
        RdCall<Unit, List<String>>(FrameworkMarshallers.Void, __StringListSerializer),
        RdCall<String, Boolean>(FrameworkMarshallers.String, FrameworkMarshallers.Bool),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool),
        RdCall<Unit, Boolean>(FrameworkMarshallers.Void, FrameworkMarshallers.Bool),
        RdCall<Unit, RdProductInfo>(FrameworkMarshallers.Void, RdProductInfo)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RdTestSession (")
        printer.indent {
            print("rdAgentInfo = "); rdAgentInfo.print(printer); println()
            print("runTestMethod = "); runTestMethod.print(printer); println()
            print("traceCategories = "); traceCategories.print(printer); println()
            print("debugCategories = "); debugCategories.print(printer); println()
            print("ready = "); _ready.print(printer); println()
            print("sendException = "); _sendException.print(printer); println()
            print("exitApp = "); _exitApp.print(printer); println()
            print("showNotification = "); _showNotification.print(printer); println()
            print("forceLeaveAllModals = "); _forceLeaveAllModals.print(printer); println()
            print("closeAllOpenedProjects = "); _closeAllOpenedProjects.print(printer); println()
            print("runNextAction = "); _runNextAction.print(printer); println()
            print("requestFocus = "); _requestFocus.print(printer); println()
            print("isFocused = "); _isFocused.print(printer); println()
            print("visibleFrameNames = "); _visibleFrameNames.print(printer); println()
            print("projectsNames = "); _projectsNames.print(printer); println()
            print("makeScreenshot = "); _makeScreenshot.print(printer); println()
            print("isResponding = "); _isResponding.print(printer); println()
            print("projectsAreInitialised = "); _projectsAreInitialised.print(printer); println()
            print("getProductCodeAndVersion = "); _getProductCodeAndVersion.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): RdTestSession   {
        return RdTestSession(
            rdAgentInfo,
            runTestMethod,
            traceCategories,
            debugCategories,
            _ready.deepClonePolymorphic(),
            _sendException.deepClonePolymorphic(),
            _exitApp.deepClonePolymorphic(),
            _showNotification.deepClonePolymorphic(),
            _forceLeaveAllModals.deepClonePolymorphic(),
            _closeAllOpenedProjects.deepClonePolymorphic(),
            _runNextAction.deepClonePolymorphic(),
            _requestFocus.deepClonePolymorphic(),
            _isFocused.deepClonePolymorphic(),
            _visibleFrameNames.deepClonePolymorphic(),
            _projectsNames.deepClonePolymorphic(),
            _makeScreenshot.deepClonePolymorphic(),
            _isResponding.deepClonePolymorphic(),
            _projectsAreInitialised.deepClonePolymorphic(),
            _getProductCodeAndVersion.deepClonePolymorphic()
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
    val cause: RdTestSessionLightException?,
    val suppressedExceptions: List<RdTestSessionLightException>?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdTestSessionException> {
        override val _type: KClass<RdTestSessionException> = RdTestSessionException::class
        override val id: RdId get() = RdId(-6820612235039581104)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdTestSessionException  {
            val type = buffer.readString()
            val originalType = buffer.readNullable { buffer.readString() }
            val message = buffer.readNullable { buffer.readString() }
            val stacktrace = buffer.readList { RdTestSessionStackTraceElement.read(ctx, buffer) }
            val cause = buffer.readNullable { RdTestSessionLightException.read(ctx, buffer) }
            val suppressedExceptions = buffer.readNullable { buffer.readList { RdTestSessionLightException.read(ctx, buffer) } }
            return RdTestSessionException(type, originalType, message, stacktrace, cause, suppressedExceptions)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestSessionException)  {
            buffer.writeString(value.type)
            buffer.writeNullable(value.originalType) { buffer.writeString(it) }
            buffer.writeNullable(value.message) { buffer.writeString(it) }
            buffer.writeList(value.stacktrace) { v -> RdTestSessionStackTraceElement.write(ctx, buffer, v) }
            buffer.writeNullable(value.cause) { RdTestSessionLightException.write(ctx, buffer, it) }
            buffer.writeNullable(value.suppressedExceptions) { buffer.writeList(it) { v -> RdTestSessionLightException.write(ctx, buffer, v) } }
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
        if (suppressedExceptions != other.suppressedExceptions) return false
        
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
        __r = __r*31 + if (suppressedExceptions != null) suppressedExceptions.hashCode() else 0
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
            print("suppressedExceptions = "); suppressedExceptions.print(printer); println()
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
data class RdTestSessionLightException (
    val type: String,
    val message: String?,
    val stacktrace: List<RdTestSessionStackTraceElement>
) : IPrintable {
    //companion
    
    companion object : IMarshaller<RdTestSessionLightException> {
        override val _type: KClass<RdTestSessionLightException> = RdTestSessionLightException::class
        override val id: RdId get() = RdId(-2964405344154034056)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RdTestSessionLightException  {
            val type = buffer.readString()
            val message = buffer.readNullable { buffer.readString() }
            val stacktrace = buffer.readList { RdTestSessionStackTraceElement.read(ctx, buffer) }
            return RdTestSessionLightException(type, message, stacktrace)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RdTestSessionLightException)  {
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
        
        other as RdTestSessionLightException
        
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
        printer.println("RdTestSessionLightException (")
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
        override val id: RdId get() = RdId(-4029698853809470560)
        
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
