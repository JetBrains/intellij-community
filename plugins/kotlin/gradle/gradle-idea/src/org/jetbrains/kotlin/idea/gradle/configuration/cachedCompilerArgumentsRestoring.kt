// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradle.configuration

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.idea.gradle.configuration.CachedCompilerArgumentsRestoringManager.restoreCompilerArgument
import org.jetbrains.kotlin.idea.gradleTooling.arguments.*
import org.jetbrains.kotlin.idea.projectModel.ArgsInfo
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware
import org.jetbrains.kotlin.idea.projectModel.KotlinCachedCompilerArgument
import org.jetbrains.kotlin.idea.projectModel.KotlinRawCompilerArgument
import org.jetbrains.kotlin.platform.impl.FakeK2NativeCompilerArguments
import java.io.File
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

sealed interface EntityArgsInfo : ArgsInfo<CommonCompilerArguments, String>

data class EntityArgsInfoImpl(
    override val currentCompilerArguments: CommonCompilerArguments,
    override val defaultCompilerArguments: CommonCompilerArguments,
    override val dependencyClasspath: Collection<String>
) : EntityArgsInfo

class EmptyEntityArgsInfo : EntityArgsInfo {
    override val currentCompilerArguments: CommonCompilerArguments = CommonCompilerArguments.DummyImpl()
    override val defaultCompilerArguments: CommonCompilerArguments = CommonCompilerArguments.DummyImpl()
    override val dependencyClasspath: Collection<String> = emptyList()
}

sealed interface FlatSerializedArgsInfo : ArgsInfo<List<String>, String>

data class FlatSerializedArgsInfoImpl(
    override val currentCompilerArguments: List<String>,
    override val defaultCompilerArguments: List<String>,
    override val dependencyClasspath: Collection<String>
) : FlatSerializedArgsInfo

class EmptyFlatArgsInfo : FlatSerializedArgsInfo {
    override val currentCompilerArguments: List<String> = emptyList()
    override val defaultCompilerArguments: List<String> = emptyList()
    override val dependencyClasspath: Collection<String> = emptyList()
}

object CachedArgumentsRestoring {
    private val LOGGER = Logger.getInstance(CachedArgumentsRestoring.javaClass)
    private const val STUB_K_2_NATIVE_COMPILER_ARGUMENTS_CLASS = "org.jetbrains.kotlin.gradle.tasks.StubK2NativeCompilerArguments"
    fun restoreSerializedArgsInfo(
        cachedSerializedArgsInfo: CachedSerializedArgsInfo,
        compilerArgumentsCacheHolder: CompilerArgumentsCacheHolder
    ): FlatSerializedArgsInfo {
        val cacheAware = compilerArgumentsCacheHolder.getCacheAware(cachedSerializedArgsInfo.cacheOriginIdentifier)
            ?: return EmptyFlatArgsInfo().also {
                LOGGER.error("CompilerArgumentsCacheAware with UUID '${cachedSerializedArgsInfo.cacheOriginIdentifier}' was not found!")
            }
        val currentCompilerArguments =
            cachedSerializedArgsInfo.currentCompilerArguments.mapNotNull { it.restoreArgumentAsString(cacheAware) }
        val defaultCompilerArguments =
            cachedSerializedArgsInfo.defaultCompilerArguments.mapNotNull { it.restoreArgumentAsString(cacheAware) }
        val dependencyClasspath = cachedSerializedArgsInfo.dependencyClasspath.mapNotNull { it.restoreArgumentAsString(cacheAware) }
        return FlatSerializedArgsInfoImpl(currentCompilerArguments, defaultCompilerArguments, dependencyClasspath)
    }

    fun restoreExtractedArgs(
        cachedExtractedArgsInfo: CachedExtractedArgsInfo,
        compilerArgumentsCacheHolder: CompilerArgumentsCacheHolder
    ): EntityArgsInfo {
        val cacheAware = compilerArgumentsCacheHolder.getCacheAware(cachedExtractedArgsInfo.cacheOriginIdentifier)
            ?: return EmptyEntityArgsInfo().also {
                LOGGER.error("CompilerArgumentsCacheAware with UUID '${cachedExtractedArgsInfo.cacheOriginIdentifier}' was not found!")
            }
        val currentCompilerArguments = restoreCachedCompilerArguments(cachedExtractedArgsInfo.currentCompilerArguments, cacheAware)
        val defaultCompilerArgumentsBucket = restoreCachedCompilerArguments(cachedExtractedArgsInfo.defaultCompilerArguments, cacheAware)
        val dependencyClasspath = cachedExtractedArgsInfo.dependencyClasspath
            .mapNotNull { it.restoreArgumentAsString(cacheAware) }
            .map { PathUtil.toSystemIndependentName(it) }
        return EntityArgsInfoImpl(currentCompilerArguments, defaultCompilerArgumentsBucket, dependencyClasspath)
    }

    private fun Map.Entry<KotlinCachedCompilerArgument<*>, KotlinCachedCompilerArgument<*>>.obtainPropertyWithCachedValue(
        propertiesByName: Map<String, KProperty1<out CommonCompilerArguments, *>>,
        cacheAware: CompilerArgumentsCacheAware
    ): Pair<KProperty1<out CommonCompilerArguments, *>, KotlinRawCompilerArgument<*>>? {
        val (key, value) = restoreEntry(this, cacheAware) ?: return null
        val restoredKey = (key as? KotlinRawRegularCompilerArgument)?.data ?: return null
        return (propertiesByName[restoredKey] ?: return null) to value
    }

    @Suppress("UNCHECKED_CAST")
    private fun restoreCachedCompilerArguments(
        cachedBucket: CachedCompilerArgumentsBucket,
        cacheAware: CompilerArgumentsCacheAware
    ): CommonCompilerArguments {
        fun KProperty1<*, *>.prepareLogMessage(): String =
            "Failed to restore value for $returnType compiler argument '$name'. Default value will be used!"

        val compilerArgumentsClassName = cachedBucket.compilerArgumentsClassName.data.let {
            cacheAware.getCached(it) ?: return CommonCompilerArguments.DummyImpl().also { _ ->
                LOGGER.error("Failed to restore name of compiler arguments class from id $it! 'CommonCompilerArguments' instance was created instead")
            }
        }
        //TODO: Fixup. Remove it once actual K2NativeCompilerArguments will be available without 'kotlin.native.enabled = true' flag
        val compilerArgumentsClass = if (compilerArgumentsClassName == STUB_K_2_NATIVE_COMPILER_ARGUMENTS_CLASS)
            FakeK2NativeCompilerArguments::class.java
        else
            Class.forName(compilerArgumentsClassName) as Class<out CommonCompilerArguments>

        val newCompilerArgumentsBean = compilerArgumentsClass.getConstructor().newInstance()
        val propertiesByName = compilerArgumentsClass.kotlin.memberProperties.associateBy { it.name }
        cachedBucket.singleArguments.entries.mapNotNull {
            val (property, value) = it.obtainPropertyWithCachedValue(propertiesByName, cacheAware) ?: return@mapNotNull null
            val newValue = when (value) {
                is KotlinRawEmptyCompilerArgument -> null
                is KotlinRawRegularCompilerArgument -> value.data
                else -> {
                    LOGGER.error(property.prepareLogMessage())
                    return@mapNotNull null
                }
            }
            property to newValue
        }.forEach { (prop, newVal) ->
            (prop as KMutableProperty1<CommonCompilerArguments, String?>).set(newCompilerArgumentsBean, newVal)
        }
        cachedBucket.flagArguments.entries.mapNotNull {
            val (property, value) = it.obtainPropertyWithCachedValue(propertiesByName, cacheAware) ?: return@mapNotNull null
            val restoredValue = (value as? KotlinRawBooleanCompilerArgument)?.data ?: run {
                LOGGER.error(property.prepareLogMessage())
                return@mapNotNull null
            }
            property to restoredValue
        }.forEach { (prop, newVal) ->
            (prop as KMutableProperty1<CommonCompilerArguments, Boolean>).set(newCompilerArgumentsBean, newVal)
        }
        cachedBucket.multipleArguments.entries.mapNotNull {
            val (property, value) = it.obtainPropertyWithCachedValue(propertiesByName, cacheAware) ?: return@mapNotNull null
            val restoredValue = when (value) {
                is KotlinRawEmptyCompilerArgument -> null
                is KotlinRawMultipleCompilerArgument -> value.data.toTypedArray()
                else -> {
                    LOGGER.error(property.prepareLogMessage())
                    return@mapNotNull null
                }
            }
            property to restoredValue
        }.forEach { (prop, newVal) ->
            (prop as KMutableProperty1<CommonCompilerArguments, Array<String>?>).set(newCompilerArgumentsBean, newVal)
        }
        val classpathValue = (restoreCompilerArgument(cachedBucket.classpathParts, cacheAware) as KotlinRawMultipleCompilerArgument?)
            ?.data
            ?.joinToString(File.pathSeparator)
        when (newCompilerArgumentsBean) {
            is K2JVMCompilerArguments -> newCompilerArgumentsBean.classpath = classpathValue
            is K2MetadataCompilerArguments -> newCompilerArgumentsBean.classpath = classpathValue
        }

        val freeArgs = cachedBucket.freeArgs.mapNotNull { it.restoreArgumentAsString(cacheAware) }
        val internalArgs = cachedBucket.internalArguments.mapNotNull { it.restoreArgumentAsString(cacheAware) }
        parseCommandLineArguments(freeArgs + internalArgs, newCompilerArgumentsBean)

        return newCompilerArgumentsBean
    }

    private fun KotlinCachedCompilerArgument<*>.restoreArgumentAsString(cacheAware: CompilerArgumentsCacheAware): String? =
        when (val arg = restoreCompilerArgument(this, cacheAware)) {
            is KotlinRawBooleanCompilerArgument -> arg.data.toString()
            is KotlinRawRegularCompilerArgument -> arg.data
            is KotlinRawMultipleCompilerArgument -> arg.data.joinToString(File.separator)
            else -> {
                LOGGER.error("Unknown argument received" + arg?.let { ": ${it::class.qualifiedName}" }.orEmpty())
                null
            }
        }

    private fun restoreEntry(
        entry: Map.Entry<KotlinCachedCompilerArgument<*>, KotlinCachedCompilerArgument<*>>,
        cacheAware: CompilerArgumentsCacheAware
    ): Pair<KotlinRawCompilerArgument<*>, KotlinRawCompilerArgument<*>>? {
        val key = restoreCompilerArgument(entry.key, cacheAware) ?: return null
        val value = restoreCompilerArgument(entry.value, cacheAware) ?: return null
        return key to value
    }
}

object CachedCompilerArgumentsRestoringManager {
    private val LOGGER = Logger.getInstance(CachedCompilerArgumentsRestoringManager.javaClass)

    @Suppress("UNCHECKED_CAST")
    fun <TCache> restoreCompilerArgument(
        argument: TCache,
        cacheAware: CompilerArgumentsCacheAware
    ): KotlinRawCompilerArgument<*>? =
        when (argument) {
            is KotlinCachedEmptyCompilerArgument -> KotlinRawEmptyCompilerArgument
            is KotlinCachedBooleanCompilerArgument -> BOOLEAN_ARGUMENT_RESTORING_STRATEGY.restoreArgument(argument, cacheAware)
            is KotlinCachedRegularCompilerArgument -> REGULAR_ARGUMENT_RESTORING_STRATEGY.restoreArgument(argument, cacheAware)
            is KotlinCachedMultipleCompilerArgument -> MULTIPLE_ARGUMENT_RESTORING_STRATEGY.restoreArgument(argument, cacheAware)
            else -> {
                LOGGER.error("Unknown argument received" + argument?.let { ": ${it::class.java.name}" })
                null
            }
        }

    private interface CompilerArgumentRestoringStrategy<TCache, TArg> {
        fun restoreArgument(cachedArgument: TCache, cacheAware: CompilerArgumentsCacheAware): KotlinRawCompilerArgument<TArg>?
    }

    private val BOOLEAN_ARGUMENT_RESTORING_STRATEGY =
        object : CompilerArgumentRestoringStrategy<KotlinCachedBooleanCompilerArgument, Boolean> {
            override fun restoreArgument(
                cachedArgument: KotlinCachedBooleanCompilerArgument,
                cacheAware: CompilerArgumentsCacheAware
            ): KotlinRawBooleanCompilerArgument? =
                cacheAware.getCached(cachedArgument.data)?.let { KotlinRawBooleanCompilerArgument(java.lang.Boolean.valueOf(it)) } ?: run {
                    LOGGER.error("Cannot find boolean argument value for key '${cachedArgument.data}'")
                    null
                }
        }

    private val REGULAR_ARGUMENT_RESTORING_STRATEGY =
        object : CompilerArgumentRestoringStrategy<KotlinCachedRegularCompilerArgument, String> {
            override fun restoreArgument(
                cachedArgument: KotlinCachedRegularCompilerArgument,
                cacheAware: CompilerArgumentsCacheAware
            ): KotlinRawRegularCompilerArgument? =
                cacheAware.getCached(cachedArgument.data)?.let { KotlinRawRegularCompilerArgument(it) } ?: run {
                    LOGGER.error("Cannot find string argument value for key '${cachedArgument.data}'")
                    null
                }
        }

    private val MULTIPLE_ARGUMENT_RESTORING_STRATEGY =
        object : CompilerArgumentRestoringStrategy<KotlinCachedMultipleCompilerArgument, List<String>> {
            override fun restoreArgument(
                cachedArgument: KotlinCachedMultipleCompilerArgument,
                cacheAware: CompilerArgumentsCacheAware
            ): KotlinRawMultipleCompilerArgument {
                val cachedArguments = cachedArgument.data.mapNotNull { restoreCompilerArgument(it, cacheAware)?.data?.toString() }
                return KotlinRawMultipleCompilerArgument(cachedArguments)
            }
        }
}