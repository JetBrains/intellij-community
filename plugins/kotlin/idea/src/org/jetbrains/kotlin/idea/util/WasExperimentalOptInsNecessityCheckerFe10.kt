// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.SINCE_KOTLIN_FQ_NAME
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


/**
 * See [org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection.WasExperimentalOptInsNecessityChecker]
 */
internal object WasExperimentalOptInsNecessityCheckerFe10 {
    private val VERSION_ARGUMENT = Name.identifier("version")


    // If there are both `@SinceKotlin` and `@WasExperimental` annotations,
    // and Kotlin API version of the module is less than the version specified by `@SinceKotlin`,
    // then the `@OptIn` for `@WasExperimental` marker is necessary.
    //
    // For example, consider a function
    // ```
    // @SinceKotlin("1.6")
    // @WasExperimental(Marker::class)
    // fun foo() { ... }
    // ```
    // This combination of annotations means that `foo` was experimental before Kotlin 1.6
    // and required `@OptIn(Marker::class) or `@Marker` annotation. When the client code
    // is compiled as Kotlin 1.6 code, there are no problems, and the `@OptIn(Marker::class)`
    // annotation would not be necessary. At the same time, when the code is compiled with
    // `apiVersion = 1.5`, the non-experimental declaration of `foo` will be hidden
    // from the resolver, so `@OptIn` is necessary for the code to compile.
    fun getNecessaryOptInsFromWasExperimental(
        annotations: Annotations,
        module: ModuleDescriptor,
        moduleApiVersion: ApiVersion,
    ): Collection<FqName> {
        val wasExperimental = annotations.findAnnotation(OptInNames.WAS_EXPERIMENTAL_FQ_NAME)
        val sinceApiVersion = getSinceKotlinAnnotationApiVersionArgumentIfPresent(annotations)

        if (wasExperimental == null || sinceApiVersion == null || moduleApiVersion >= sinceApiVersion) {
            return emptyList()
        }
        return getWasExperimentalAnnotationMarkerClassArgument(wasExperimental, module)
    }

    private fun getSinceKotlinAnnotationApiVersionArgumentIfPresent(annotations: Annotations): ApiVersion? {
        val sinceKotlin = annotations.findAnnotation(SINCE_KOTLIN_FQ_NAME) ?: return null
        return sinceKotlin.allValueArguments[VERSION_ARGUMENT]
            ?.safeAs<StringValue>()
            ?.value
            ?.let { ApiVersion.parse(it) }
    }

    private fun getWasExperimentalAnnotationMarkerClassArgument(
        annotation: AnnotationDescriptor,
        module: ModuleDescriptor,
    ): Collection<FqName> {
        return annotation.allValueArguments[OptInNames.WAS_EXPERIMENTAL_ANNOTATION_CLASS]
            ?.safeAs<ArrayValue>()
            ?.value
            ?.mapNotNull { (it as? KClassValue)?.getArgumentType(module)?.fqName }
            ?: emptyList()
    }
}
