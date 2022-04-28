// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.idea.gradle.configuration.CachedArgumentsRestoring
import org.jetbrains.kotlin.idea.gradleTooling.arguments.*
import org.jetbrains.kotlin.idea.test.testFramework.KtUsefulTestCase
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompilerArgumentsCachingTest {

    @Test
    fun `extract CommonCompilerArguments, modify and check bucket`() {
        extractAndCheck(CommonCompilerArguments.DummyImpl::class.java) {
            configureCommon()
        }
    }

    @Test
    fun `extract CommonCompilerArguments, modify, cache and check`() {
        extractAndCheck(CommonCompilerArguments.DummyImpl::class.java) {
            configureCommon()
        }.cacheAndCheckConsistency()
    }

    @Test
    fun `cache CommonCompilerArguments, restore and compare`() {
        createCacheRestoreCompare(CommonCompilerArguments.DummyImpl::class.java) {
            configureCommon()
        }
    }

    @Test
    fun `extract K2MetadataCompilerArguments, modify and check bucket`() {
        extractAndCheck(K2MetadataCompilerArguments::class.java) {
            configureMetadata()
        }
    }

    @Test
    fun `extract K2MetadataCompilerArguments, modify, cache and check`() {
        extractAndCheck(K2MetadataCompilerArguments::class.java) {
            configureMetadata()
        }.cacheAndCheckConsistency()
    }

    @Test
    fun `cache K2MetadataCompilerArguments, restore and compare`() {
        createCacheRestoreCompare(K2MetadataCompilerArguments::class.java) {
            configureMetadata()
        }
    }

    @Test
    fun `extract K2JVMCompilerArguments and modify and check bucket`() {
        extractAndCheck(K2JVMCompilerArguments::class.java) {
            configureJvm()
        }
    }

    @Test
    fun `extract K2JVMCompilerArguments and modify, cache and check`() {
        extractAndCheck(K2JVMCompilerArguments::class.java) {
            configureJvm()
        }.cacheAndCheckConsistency()
    }

    @Test
    fun `cache K2JVMCompilerArguments, restore and compare`() {
        createCacheRestoreCompare(K2JVMCompilerArguments::class.java) {
            configureJvm()
        }
    }

    @Test
    fun `extract K2JSCompilerArguments and modify and check bucket`() {
        extractAndCheck(K2JSCompilerArguments::class.java) {
            configureJs()
        }
    }

    @Test
    fun `extract K2JSCompilerArguments and modify, cache and check`() {
        extractAndCheck(K2JSCompilerArguments::class.java) {
            configureJs()
        }.cacheAndCheckConsistency()
    }

    @Test
    fun `cache K2JSCompilerArguments, restore and compare`() {
        createCacheRestoreCompare(K2JSCompilerArguments::class.java) {
            configureJs()
        }
    }

    private fun ExtractedCompilerArgumentsBucket.cacheAndCheckConsistency() {
        val mapper = CompilerArgumentsCacheMapperImpl()
        val cachedBucket = CompilerArgumentsCachingTool.cacheCompilerArguments(this, mapper)
        singleArguments.entries.forEach { (key, value) ->
            assertTrue(mapper.checkCached(key))
            val rawValue = value ?: return@forEach
            assertTrue(mapper.checkCached(rawValue))
            val keyCacheId = mapper.cacheArgument(key)
            val matchingCachedEntry = cachedBucket.singleArguments.entries.singleOrNull { (cachedKey, _) ->
                cachedKey is KotlinCachedRegularCompilerArgument && cachedKey.data == keyCacheId
            }
            assertTrue(matchingCachedEntry != null)
            val valueCacheId = mapper.cacheArgument(rawValue)
            assertEquals(
                (matchingCachedEntry.value as KotlinCachedRegularCompilerArgument).data,
                valueCacheId
            )
        }
        Assert.assertArrayEquals(
            classpathParts,
            cachedBucket.classpathParts.data.map { mapper.getCached((it as KotlinCachedRegularCompilerArgument).data) }.toTypedArray()
        )
        flagArguments.entries.forEach { (key, value) ->
            assertTrue(mapper.checkCached(key))
            val rawValue = value.toString()
            assertTrue(mapper.checkCached(rawValue))
            val keyCacheId = mapper.cacheArgument(key)
            val matchingCachedEntry = cachedBucket.flagArguments.entries.singleOrNull { (cachedKey, _) ->
                cachedKey is KotlinCachedRegularCompilerArgument && cachedKey.data == keyCacheId
            }
            assertTrue(matchingCachedEntry != null)
            val valueCacheId = mapper.cacheArgument(rawValue)
            assertEquals(
                (matchingCachedEntry.value as KotlinCachedBooleanCompilerArgument).data,
                valueCacheId
            )
        }
        multipleArguments.entries.forEach { (key, value) ->
            assertTrue(mapper.checkCached(key))
            val rawValues = value ?: return@forEach
            rawValues.forEach {
                assertTrue(mapper.checkCached(it))
            }
            val keyCacheId = mapper.cacheArgument(key)
            val matchingCachedEntry = cachedBucket.multipleArguments.entries.singleOrNull { (cachedKey, _) ->
                cachedKey is KotlinCachedRegularCompilerArgument && cachedKey.data == keyCacheId
            }
            assertTrue(matchingCachedEntry != null)
            val valueCacheIds = rawValues.map { mapper.cacheArgument(it) }
            Assert.assertArrayEquals(
                (matchingCachedEntry.value as KotlinCachedMultipleCompilerArgument).data
                    .map { (it as KotlinCachedRegularCompilerArgument).data }.toTypedArray(),
                valueCacheIds.toTypedArray()
            )
        }

        assertContentEquals(
            freeArgs,
            cachedBucket.freeArgs.map { mapper.getCached((it as KotlinCachedRegularCompilerArgument).data) }
        )

        assertContentEquals(
            internalArguments,
            cachedBucket.internalArguments.map { mapper.getCached((it as KotlinCachedRegularCompilerArgument).data) }
        )
    }

    private fun CommonCompilerArguments.configureCommon() {
        help = Random.nextBoolean()
        extraHelp = Random.nextBoolean()
        version = Random.nextBoolean()
        verbose = Random.nextBoolean()
        suppressWarnings = Random.nextBoolean()
        allWarningsAsErrors = Random.nextBoolean()
        progressiveMode = Random.nextBoolean()
        script = Random.nextBoolean()
        noInline = Random.nextBoolean()
        skipMetadataVersionCheck = Random.nextBoolean()
        skipPrereleaseCheck = Random.nextBoolean()
        newInference = Random.nextBoolean()
        allowKotlinPackage = Random.nextBoolean()
        reportOutputFiles = Random.nextBoolean()
        multiPlatform = Random.nextBoolean()
        noCheckActual = Random.nextBoolean()
        inlineClasses = Random.nextBoolean()
        legacySmartCastAfterTry = Random.nextBoolean()
        effectSystem = Random.nextBoolean()
        readDeserializedContracts = Random.nextBoolean()
        allowResultReturnType = Random.nextBoolean()
        properIeee754Comparisons = Random.nextBoolean()
        reportPerf = Random.nextBoolean()
        listPhases = Random.nextBoolean()
        profilePhases = Random.nextBoolean()
        checkPhaseConditions = Random.nextBoolean()
        checkStickyPhaseConditions = Random.nextBoolean()
        useK2 = Random.nextBoolean()
        useFirExtendedCheckers = Random.nextBoolean()
        disableUltraLightClasses = Random.nextBoolean()
        useMixedNamedArguments = Random.nextBoolean()
        expectActualLinker = Random.nextBoolean()
        disableDefaultScriptingPlugin = Random.nextBoolean()
        inferenceCompatibility = Random.nextBoolean()
        extendedCompilerChecks = Random.nextBoolean()
        suppressVersionWarnings = Random.nextBoolean()

        // Invalid values doesn't matter
        languageVersion = generateRandomString(20)
        apiVersion = generateRandomString(20)
        kotlinHome = generateRandomString(20)
        intellijPluginRoot = generateRandomString(20)
        dumpPerf = generateRandomString(20)
        metadataVersion = generateRandomString(20)
        dumpDirectory = generateRandomString(20)
        dumpOnlyFqName = generateRandomString(20)
        explicitApi = generateRandomString(20)

        pluginOptions = generateRandomStringArray(20)
        pluginClasspaths = generateRandomStringArray(20)
        optIn = generateRandomStringArray(20)
        commonSources = generateRandomStringArray(20)
        disablePhases = generateRandomStringArray(20)
        verbosePhases = generateRandomStringArray(20)
        phasesToDumpBefore = generateRandomStringArray(20)
        phasesToDumpAfter = generateRandomStringArray(20)
        phasesToDump = generateRandomStringArray(20)
        namesExcludedFromDumping = generateRandomStringArray(20)
        phasesToValidateBefore = generateRandomStringArray(20)
        phasesToValidateAfter = generateRandomStringArray(20)
        phasesToValidate = generateRandomStringArray(20)
    }

    private fun K2MetadataCompilerArguments.configureMetadata() {
        configureCommon()
        enabledInJps = Random.nextBoolean()

        destination = generateRandomString(20)
        moduleName = generateRandomString(20)

        classpath = generateRandomStringArray(20).joinToString(File.pathSeparator)

        friendPaths = generateRandomStringArray(20)
        refinesPaths = generateRandomStringArray(20)
    }

    private fun K2JVMCompilerArguments.configureJvm() {
        configureCommon()
        includeRuntime = Random.nextBoolean()
        noJdk = Random.nextBoolean()
        noStdlib = Random.nextBoolean()
        noReflect = Random.nextBoolean()
        javaParameters = Random.nextBoolean()
        useIR = Random.nextBoolean()
        useOldBackend = Random.nextBoolean()
        allowUnstableDependencies = Random.nextBoolean()
        doNotClearBindingContext = Random.nextBoolean()
        noCallAssertions = Random.nextBoolean()
        noReceiverAssertions = Random.nextBoolean()
        noParamAssertions = Random.nextBoolean()
        noOptimize = Random.nextBoolean()
        inheritMultifileParts = Random.nextBoolean()
        useTypeTable = Random.nextBoolean()
        useOldClassFilesReading = Random.nextBoolean()
        singleModule = Random.nextBoolean()
        suppressMissingBuiltinsError = Random.nextBoolean()
        useJavac = Random.nextBoolean()
        compileJava = Random.nextBoolean()
        disableStandardScript = Random.nextBoolean()
        strictMetadataVersionSemantics = Random.nextBoolean()
        sanitizeParentheses = Random.nextBoolean()
        allowNoSourceFiles = Random.nextBoolean()
        emitJvmTypeAnnotations = Random.nextBoolean()
        noOptimizedCallableReferences = Random.nextBoolean()
        noKotlinNothingValueException = Random.nextBoolean()
        noResetJarTimestamps = Random.nextBoolean()
        noUnifiedNullChecks = Random.nextBoolean()
        useOldInlineClassesManglingScheme = Random.nextBoolean()
        enableJvmPreview = Random.nextBoolean()
        suppressDeprecatedJvmTargetWarning = Random.nextBoolean()
        typeEnhancementImprovementsInStrictMode = Random.nextBoolean()

        destination = generateRandomString(20)
        jdkHome = generateRandomString(20)
        expression = generateRandomString(20)
        moduleName = generateRandomString(20)
        jvmTarget = generateRandomString(20)
        abiStability = generateRandomString(20)
        javaModulePath = generateRandomString(20)
        assertionsMode = generateRandomString(20)
        buildFile = generateRandomString(20)
        declarationsOutputPath = generateRandomString(20)
        javaPackagePrefix = generateRandomString(20)
        supportCompatqualCheckerFrameworkAnnotations = generateRandomString(20)
        jspecifyAnnotations = generateRandomString(20)
        jvmDefault = generateRandomString(20)
        defaultScriptExtension = generateRandomString(20)
        stringConcat = generateRandomString(20)
        klibLibraries = generateRandomString(20)
        profileCompilerCommand = generateRandomString(20)
        repeatCompileModules = generateRandomString(20)
        samConversions = generateRandomString(20)
        lambdas = generateRandomString(20)

        classpath = generateRandomStringArray(20).joinToString(File.pathSeparator)

        scriptTemplates = generateRandomStringArray(10)
        additionalJavaModules = generateRandomStringArray(10)
        scriptResolverEnvironment = generateRandomStringArray(10)
        javacArguments = generateRandomStringArray(10)
        javaSourceRoots = generateRandomStringArray(10)
        jsr305 = generateRandomStringArray(10)
        friendPaths = generateRandomStringArray(10)
    }

    private fun K2JSCompilerArguments.configureJs() {
        configureCommon()
        noStdlib = Random.nextBoolean()
        sourceMap = Random.nextBoolean()
        metaInfo = Random.nextBoolean()
        irProduceKlibDir = Random.nextBoolean()
        irProduceKlibFile = Random.nextBoolean()
        irProduceJs = Random.nextBoolean()
        irDce = Random.nextBoolean()
        irDcePrintReachabilityInfo = Random.nextBoolean()
        irPropertyLazyInitialization = Random.nextBoolean()
        irOnly = Random.nextBoolean()
        irPerModule = Random.nextBoolean()
        generateDts = Random.nextBoolean()
        typedArrays = Random.nextBoolean()
        friendModulesDisabled = Random.nextBoolean()
        metadataOnly = Random.nextBoolean()
        enableJsScripting = Random.nextBoolean()
        fakeOverrideValidator = Random.nextBoolean()
        wasm = Random.nextBoolean()

        outputFile = generateRandomString(20)
        libraries = generateRandomString(20)
        sourceMapPrefix = generateRandomString(20)
        sourceMapBaseDirs = generateRandomString(20)
        sourceMapEmbedSources = generateRandomString(20)
        target = generateRandomString(20)
        moduleKind = generateRandomString(20)
        main = generateRandomString(20)
        outputPrefix = generateRandomString(20)
        outputPostfix = generateRandomString(20)
        irModuleName = generateRandomString(20)
        includes = generateRandomString(20)
        friendModules = generateRandomString(20)
        errorTolerancePolicy = generateRandomString(20)
    }

    private fun <T : CommonCompilerArguments> extractAndCheck(
        argumentsClass: Class<out T>,
        modify: T.() -> Unit = {}
    ): ExtractedCompilerArgumentsBucket {
        val compilerArguments = argumentsClass.getDeclaredConstructor().newInstance()
        compilerArguments.modify()
        val bucket = CompilerArgumentsExtractor.prepareCompilerArgumentsBucket(compilerArguments)
        val propertiesByName = argumentsClass.kotlin.memberProperties.associateBy { it.name }
        KtUsefulTestCase.assertContainsElements(
            singleCompilerArgumentsMap[argumentsClass.simpleName]!!.sorted(),
            bucket.singleArguments.keys.toList().sorted(),
        )
        bucket.singleArguments.entries.forEach {
            assertEquals(
                propertiesByName[it.key]!!.cast<KProperty1<T, String>>().get(compilerArguments),
                it.value
            )
        }
        assertEquals(
            propertiesByName["classpath"]?.cast<KProperty1<T, String>>()?.get(compilerArguments),
            bucket.classpathParts.ifNotEmpty { joinToString(File.pathSeparator) }
        )
        KtUsefulTestCase.assertContainsElements(
            multipleCompilerArgumentsMap[argumentsClass.simpleName]!!.sorted(),
            bucket.multipleArguments.keys.toList().sorted(),
        )
        bucket.multipleArguments.entries.forEach {
            Assert.assertArrayEquals(propertiesByName[it.key]!!.cast<KProperty1<T, Array<String>>>().get(compilerArguments), it.value)
        }

        KtUsefulTestCase.assertContainsElements(
            flagCompilerArgumentsMap[argumentsClass.simpleName]!!.sorted(),
            bucket.flagArguments.keys.toList().sorted(),
        )
        bucket.flagArguments.entries.forEach {
            assertEquals(propertiesByName[it.key]!!.cast<KProperty1<T, Boolean>>().get(compilerArguments), it.value)
        }
        KtUsefulTestCase.assertOrderedEquals(
            propertiesByName["freeArgs"]!!.cast<KProperty1<T, List<String>>>().get(compilerArguments),
            bucket.freeArgs.toList()
        )
        KtUsefulTestCase.assertOrderedEquals(
            propertiesByName["internalArguments"]!!.cast<KProperty1<T, List<InternalArgument>>>().get(compilerArguments)
                .map { it.stringRepresentation },
            bucket.internalArguments.toList()
        )
        return bucket
    }

    private fun <T : CommonCompilerArguments> createCacheRestoreCompare(klass: Class<out T>, modify: T.() -> Unit = {}) {
        val compilerArguments = klass.getDeclaredConstructor().newInstance()
        compilerArguments.modify()
        val extractedBucket = CompilerArgumentsExtractor.prepareCompilerArgumentsBucket(compilerArguments)
        val mapper = CompilerArgumentsCacheMapperImpl()
        val cachedArgsBucket = CompilerArgumentsCachingTool.cacheCompilerArguments(extractedBucket, mapper)
        val restoreCompilerArguments = CachedArgumentsRestoring::class.java.declaredMethods
            .find { it.name == "restoreCachedCompilerArguments" }!!.apply { isAccessible = true }
        val restoredCompilerArguments = restoreCompilerArguments.invoke(
            CachedArgumentsRestoring::class.java.kotlin.objectInstance,
            cachedArgsBucket,
            mapper
        ) as CommonCompilerArguments
        assertTrue(checkEquals(compilerArguments, restoredCompilerArguments))
    }


    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : CommonCompilerArguments> checkEquals(first: T, second: T): Boolean {
        val annotatedProperties = T::class.java.kotlin.memberProperties.filter { it.annotations.any { anno -> anno is Argument } }
        return annotatedProperties.all { prop ->
            when (prop.returnType.classifier) {
                Array<String>::class -> Arrays.equals(prop.get(first) as? Array<String>, prop.get(second) as? Array<String>)
                else -> prop.get(first) == prop.get(second)
            }
        } && first.freeArgs == second.freeArgs && first.internalArguments == second.internalArguments
    }

    companion object {
        private val alphabet = ('a'..'z').joinToString(separator = "") + ('A'..'Z').joinToString(separator = "")
        private fun generateRandomString(length: Int): String =
            generateSequence { Random.nextInt(alphabet.indices) }.take(length).map { alphabet[it] }.joinToString("")

        private fun generateRandomStringArray(size: Int): Array<String> =
            generateSequence { generateRandomString(10) }.take(size).toList().toTypedArray()


        private val commonFlagCompilerArgumentNames = listOf(
            "help",
            "extraHelp",
            "version",
            "verbose",
            "suppressWarnings",
            "allWarningsAsErrors",
            "progressiveMode",
            "script",
            "noInline",
            "skipMetadataVersionCheck",
            "skipPrereleaseCheck",
            "newInference",
            "allowKotlinPackage",
            "reportOutputFiles",
            "multiPlatform",
            "noCheckActual",
            "inlineClasses",
            "polymorphicSignature",
            "legacySmartCastAfterTry",
            "effectSystem",
            "readDeserializedContracts",
            "allowResultReturnType",
            "properIeee754Comparisons",
            "reportPerf",
            "listPhases",
            "profilePhases",
            "checkPhaseConditions",
            "checkStickyPhaseConditions",
            "useFir",
            "useFirExtendedCheckers",
            "disableUltraLightClasses",
            "useMixedNamedArguments",
            "expectActualLinker",
            "disableDefaultScriptingPlugin",
            "inferenceCompatibility",
            "extendedCompilerChecks",
            "suppressVersionWarnings"
        )
        private val commonSingleCompilerArgumentNames = listOf(
            "languageVersion",
            "apiVersion",
            "kotlinHome",
            "intellijPluginRoot",
            "dumpPerf",
            "metadataVersion",
            "dumpDirectory",
            "dumpOnlyFqName",
            "explicitApi",
        )

        private val commonMultipleCompilerArgumentNames = listOf(
            "pluginOptions",
            "pluginClasspaths",
            "experimental",
            "useExperimental",
            "optIn",
            "commonSources",
            "disablePhases",
            "verbosePhases",
            "phasesToDumpBefore",
            "phasesToDumpAfter",
            "phasesToDump",
            "namesExcludedFromDumping",
            "phasesToValidateBefore",
            "phasesToValidateAfter",
            "phasesToValidate"
        )
        private val k2MetadataFlagCompilerArgumentNames = commonFlagCompilerArgumentNames + listOf(
            "enabledInJps",
        )

        private val k2MetadataSingleCompilerArgumentNames = commonSingleCompilerArgumentNames + listOf(
            "destination",
            "moduleName"
        )

        private val k2MetadataMultipleCompilerArgumentNames = commonMultipleCompilerArgumentNames + listOf(
            "friendPaths",
            "refinesPaths"
        )

        private val k2JvmFlagCompilerArgumentNames = commonFlagCompilerArgumentNames + listOf(
            "includeRuntime",
            "noJdk",
            "noStdlib",
            "noReflect",
            "javaParameters",
            "useIR",
            "useOldBackend",
            "allowUnstableDependencies",
            "doNotClearBindingContext",
            "noCallAssertions",
            "noReceiverAssertions",
            "noParamAssertions",
            "strictJavaNullabilityAssertions",
            "noOptimize",
            "inheritMultifileParts",
            "useTypeTable",
            "skipRuntimeVersionCheck",
            "useOldClassFilesReading",
            "singleModule",
            "suppressMissingBuiltinsError",
            "useJavac",
            "compileJava",
            "noExceptionOnExplicitEqualsForBoxedNull",
            "disableStandardScript",
            "strictMetadataVersionSemantics",
            "sanitizeParentheses",
            "allowNoSourceFiles",
            "emitJvmTypeAnnotations",
            "noOptimizedCallableReferences",
            "noKotlinNothingValueException",
            "noResetJarTimestamps",
            "noUnifiedNullChecks",
            "useOldSpilledVarTypeAnalysis",
            "useOldInlineClassesManglingScheme",
            "enableJvmPreview",
            "suppressDeprecatedJvmTargetWarning",
            "typeEnhancementImprovementsInStrictMode"
        )

        private val k2JvmSingleCompilerArgumentNames = commonSingleCompilerArgumentNames + listOf(
            "destination",
            "jdkHome",
            "expression",
            "moduleName",
            "jvmTarget",
            "abiStability",
            "javaModulePath",
            "constructorCallNormalizationMode",
            "assertionsMode",
            "buildFile",
            "declarationsOutputPath",
            "javaPackagePrefix",
            "supportCompatqualCheckerFrameworkAnnotations",
            "jspecifyAnnotations",
            "jvmDefault",
            "defaultScriptExtension",
            "stringConcat",
            "klibLibraries",
            "profileCompilerCommand",
            "repeatCompileModules",
            "lambdas",
            "samConversions",
        )

        private val k2JvmMultipleCompilerArgumentNames = commonMultipleCompilerArgumentNames + listOf(
            "scriptTemplates",
            "additionalJavaModules",
            "scriptResolverEnvironment",
            "javacArguments",
            "javaSourceRoots",
            "jsr305",
            "friendPaths",
        )

        private val k2JsFlagCompilerArgumentNames = commonFlagCompilerArgumentNames + listOf(
            "noStdlib",
            "sourceMap",
            "metaInfo",
            "irProduceKlibDir",
            "irProduceKlibFile",
            "irProduceJs",
            "irDce",
            "irDceDriven",
            "irDcePrintReachabilityInfo",
            "irPropertyLazyInitialization",
            "irOnly",
            "irPerModule",
            "generateDts",
            "typedArrays",
            "friendModulesDisabled",
            "metadataOnly",
            "enableJsScripting",
            "fakeOverrideValidator",
            "wasm"
        )

        private val k2JsSingleCompilerArgumentNames = commonSingleCompilerArgumentNames + listOf(
            "outputFile",
            "libraries",
            "sourceMapPrefix",
            "sourceMapBaseDirs",
            "sourceMapEmbedSources",
            "target",
            "moduleKind",
            "main",
            "outputPrefix",
            "outputPostfix",
            "irModuleName",
            "includes",
            "friendModules",
            "errorTolerancePolicy",
            "irDceRuntimeDiagnostic",
            "repositries"
        )

        private val k2JsMultipleCompilerArgumentNames = commonMultipleCompilerArgumentNames

        private val flagCompilerArgumentsMap = mapOf(
            "DummyImpl" to commonFlagCompilerArgumentNames,
            "K2MetadataCompilerArguments" to k2MetadataFlagCompilerArgumentNames,
            "K2JVMCompilerArguments" to k2JvmFlagCompilerArgumentNames,
            "K2JSCompilerArguments" to k2JsFlagCompilerArgumentNames
        )
        private val singleCompilerArgumentsMap = mapOf(
            "DummyImpl" to commonSingleCompilerArgumentNames,
            "K2MetadataCompilerArguments" to k2MetadataSingleCompilerArgumentNames,
            "K2JVMCompilerArguments" to k2JvmSingleCompilerArgumentNames,
            "K2JSCompilerArguments" to k2JsSingleCompilerArgumentNames
        )
        private val multipleCompilerArgumentsMap = mapOf(
            "DummyImpl" to commonMultipleCompilerArgumentNames,
            "K2MetadataCompilerArguments" to k2MetadataMultipleCompilerArgumentNames,
            "K2JVMCompilerArguments" to k2JvmMultipleCompilerArgumentNames,
            "K2JSCompilerArguments" to k2JsMultipleCompilerArgumentNames
        )
    }
}
