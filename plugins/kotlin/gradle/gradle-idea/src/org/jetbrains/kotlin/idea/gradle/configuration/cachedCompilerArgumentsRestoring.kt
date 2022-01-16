// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradle.configuration

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
import java.io.File
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

data class EntityArgsInfoImpl(
    override val currentCompilerArguments: CommonCompilerArguments,
    override val defaultCompilerArguments: CommonCompilerArguments,
    override val dependencyClasspath: Collection<String>
) : ArgsInfo<CommonCompilerArguments, String>

data class FlatSerializedArgsInfoImpl(
    override val currentCompilerArguments: List<String>,
    override val defaultCompilerArguments: List<String>,
    override val dependencyClasspath: Collection<String>
) : ArgsInfo<List<String>, String>

object CachedArgumentsRestoring {
    fun restoreSerializedArgsInfo(
        cachedSerializedArgsInfo: CachedSerializedArgsInfo,
        compilerArgumentsCacheHolder: CompilerArgumentsCacheHolder
    ): FlatSerializedArgsInfoImpl {
        val cacheAware = compilerArgumentsCacheHolder.getCacheAware(cachedSerializedArgsInfo.cacheOriginIdentifier)
            ?: error("Failed to find CompilerArgumentsCacheAware for UUID '${cachedSerializedArgsInfo.cacheOriginIdentifier}'")
        val currentCompilerArguments = cachedSerializedArgsInfo.currentCompilerArguments.map { it.restoreArgumentAsString(cacheAware) }
        val defaultCompilerArguments = cachedSerializedArgsInfo.defaultCompilerArguments.map { it.restoreArgumentAsString(cacheAware) }
        val dependencyClasspath = cachedSerializedArgsInfo.dependencyClasspath.map { it.restoreArgumentAsString(cacheAware) }
        return FlatSerializedArgsInfoImpl(currentCompilerArguments, defaultCompilerArguments, dependencyClasspath)
    }

    fun restoreExtractedArgs(
        cachedExtractedArgsInfo: CachedExtractedArgsInfo,
        compilerArgumentsCacheHolder: CompilerArgumentsCacheHolder
    ): EntityArgsInfoImpl {
        val cacheAware = compilerArgumentsCacheHolder.getCacheAware(cachedExtractedArgsInfo.cacheOriginIdentifier)
            ?: error("Failed to find CompilerArgumentsCacheAware for UUID '${cachedExtractedArgsInfo.cacheOriginIdentifier}'")
        val currentCompilerArguments = restoreCachedCompilerArguments(cachedExtractedArgsInfo.currentCompilerArguments, cacheAware)
        val defaultCompilerArgumentsBucket = restoreCachedCompilerArguments(cachedExtractedArgsInfo.defaultCompilerArguments, cacheAware)
        val dependencyClasspath = cachedExtractedArgsInfo.dependencyClasspath
            .map { it.restoreArgumentAsString(cacheAware) }
            .map { PathUtil.toSystemIndependentName(it) }
        return EntityArgsInfoImpl(currentCompilerArguments, defaultCompilerArgumentsBucket, dependencyClasspath)
    }

    @Suppress("UNCHECKED_CAST")
    private fun restoreCachedCompilerArguments(
        cachedBucket: CachedCompilerArgumentsBucket,
        cacheAware: CompilerArgumentsCacheAware
    ): CommonCompilerArguments {
        val compilerArgumentsClassName = cachedBucket.compilerArgumentsClassName.data.let {
            cacheAware.getCached(it) ?: error("Failed to restore name of compiler arguments class from id $it")
        }
        val compilerArgumentsClass = Class.forName(compilerArgumentsClassName) as Class<out CommonCompilerArguments>
        val newCompilerArgumentsBean = compilerArgumentsClass.getConstructor().newInstance()
        val propertiesByName = compilerArgumentsClass.kotlin.memberProperties.associateBy { it.name }
        cachedBucket.singleArguments.entries.map { restoreEntry(it, cacheAware) }.mapNotNull {
            val key = propertiesByName[(it.first as KotlinRawRegularCompilerArgument).data] ?: return@mapNotNull null
            val newValue = when (val valueToRestore = it.second) {
                is KotlinRawRegularCompilerArgument -> valueToRestore.data
                is KotlinRawEmptyCompilerArgument -> null
                else -> error("Cannot restore value for compiler argument '$key'. Array is expected, but '${valueToRestore.data}' was received")
            }
            key to newValue
        }.forEach { (prop, newVal) ->
            (prop as KMutableProperty1<CommonCompilerArguments, String?>).set(newCompilerArgumentsBean, newVal)
        }
        cachedBucket.flagArguments.entries.map { restoreEntry(it, cacheAware) }.mapNotNull {
            val key = propertiesByName[(it.first as KotlinRawRegularCompilerArgument).data] ?: return@mapNotNull null
            key to (it.second as KotlinRawBooleanCompilerArgument).data
        }.forEach { (prop, newVal) ->
            (prop as KMutableProperty1<CommonCompilerArguments, Boolean>).set(newCompilerArgumentsBean, newVal)
        }
        cachedBucket.multipleArguments.entries.map { restoreEntry(it, cacheAware) }.mapNotNull {
            val key = propertiesByName[(it.first as KotlinRawRegularCompilerArgument).data] ?: return@mapNotNull null
            val newValue = when (val valueToRestore = it.second) {
                is KotlinRawMultipleCompilerArgument -> valueToRestore.data.toTypedArray()
                is KotlinRawEmptyCompilerArgument -> null
                else -> error("Cannot restore value for compiler argument '$key'. Array is expected, but '${valueToRestore.data}' was received")
            }
            key to newValue
        }.forEach { (prop, newVal) ->
            (prop as KMutableProperty1<CommonCompilerArguments, Array<String>?>).set(newCompilerArgumentsBean, newVal)
        }
        val classpathValue = (restoreCompilerArgument(cachedBucket.classpathParts, cacheAware) as KotlinRawMultipleCompilerArgument)
            .data
            .joinToString(File.pathSeparator)
        when (newCompilerArgumentsBean) {
            is K2JVMCompilerArguments -> newCompilerArgumentsBean.classpath = classpathValue
            is K2MetadataCompilerArguments -> newCompilerArgumentsBean.classpath = classpathValue
        }

        val freeArgs = cachedBucket.freeArgs.map { it.restoreArgumentAsString(cacheAware) }
        val internalArgs = cachedBucket.internalArguments.map { it.restoreArgumentAsString(cacheAware) }
        parseCommandLineArguments(freeArgs + internalArgs, newCompilerArgumentsBean)

        return newCompilerArgumentsBean
    }

    private fun KotlinCachedCompilerArgument<*>.restoreArgumentAsString(cacheAware: CompilerArgumentsCacheAware): String =
        when (val arg = restoreCompilerArgument(this, cacheAware)) {
            is KotlinRawBooleanCompilerArgument -> arg.data.toString()
            is KotlinRawRegularCompilerArgument -> arg.data
            is KotlinRawMultipleCompilerArgument -> arg.data.joinToString(File.separator)
            else -> error("Unknown argument received: ${arg::class.qualifiedName}")
        }

    private inline fun <reified TKey, reified TVal> restoreEntry(entry: Map.Entry<TKey, TVal>, cacheAware: CompilerArgumentsCacheAware) =
        restoreCompilerArgument(entry.key, cacheAware) to restoreCompilerArgument(entry.value, cacheAware)
}

object CachedCompilerArgumentsRestoringManager {

    @Suppress("UNCHECKED_CAST")
    fun <TCache> restoreCompilerArgument(
        argument: TCache,
        cacheAware: CompilerArgumentsCacheAware
    ): KotlinRawCompilerArgument<*> =
        when (argument) {
            is KotlinCachedEmptyCompilerArgument -> KotlinRawEmptyCompilerArgument
            is KotlinCachedBooleanCompilerArgument -> BOOLEAN_ARGUMENT_RESTORING_STRATEGY.restoreArgument(argument, cacheAware)
            is KotlinCachedRegularCompilerArgument -> REGULAR_ARGUMENT_RESTORING_STRATEGY.restoreArgument(argument, cacheAware)
            is KotlinCachedMultipleCompilerArgument -> MULTIPLE_ARGUMENT_RESTORING_STRATEGY.restoreArgument(argument, cacheAware)
            else -> error("Unknown argument received" + argument?.let { ": ${it::class.java.name}" })
        }

    private interface CompilerArgumentRestoringStrategy<TCache, TArg> {
        fun restoreArgument(cachedArgument: TCache, cacheAware: CompilerArgumentsCacheAware): KotlinRawCompilerArgument<TArg>
    }

    private val BOOLEAN_ARGUMENT_RESTORING_STRATEGY =
        object : CompilerArgumentRestoringStrategy<KotlinCachedBooleanCompilerArgument, Boolean> {
            override fun restoreArgument(
                cachedArgument: KotlinCachedBooleanCompilerArgument,
                cacheAware: CompilerArgumentsCacheAware
            ): KotlinRawBooleanCompilerArgument {
                val restoredValue = cacheAware.getCached(cachedArgument.data) ?: error("Cache doesn't contain key ${cachedArgument.data}")
                return KotlinRawBooleanCompilerArgument(java.lang.Boolean.valueOf(restoredValue))
            }
        }

    private val REGULAR_ARGUMENT_RESTORING_STRATEGY =
        object : CompilerArgumentRestoringStrategy<KotlinCachedRegularCompilerArgument, String> {
            override fun restoreArgument(
                cachedArgument: KotlinCachedRegularCompilerArgument,
                cacheAware: CompilerArgumentsCacheAware
            ): KotlinRawRegularCompilerArgument {
                return cacheAware.getCached(cachedArgument.data)?.let { KotlinRawRegularCompilerArgument(it) }
                    ?: error("Cache doesn't contain key ${cachedArgument.data}")
            }
        }

    private val MULTIPLE_ARGUMENT_RESTORING_STRATEGY =
        object : CompilerArgumentRestoringStrategy<KotlinCachedMultipleCompilerArgument, List<String>> {
            override fun restoreArgument(
                cachedArgument: KotlinCachedMultipleCompilerArgument,
                cacheAware: CompilerArgumentsCacheAware
            ): KotlinRawMultipleCompilerArgument {
                val cachedArguments = cachedArgument.data.map { restoreCompilerArgument(it, cacheAware).data.toString() }
                return KotlinRawMultipleCompilerArgument(cachedArguments)
            }
        }
}