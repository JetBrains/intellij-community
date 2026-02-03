// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.NameUtil
import org.apache.velocity.VelocityContext
import org.jetbrains.java.generate.GenerateToStringWorker
import org.jetbrains.java.generate.element.GenerationHelper
import org.jetbrains.java.generate.exception.GenerateCodeException
import org.jetbrains.java.generate.velocity.VelocityFactory
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.platform.wasm.isWasm
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import java.io.StringWriter


object VelocityGeneratorHelper {
    context(_: KaSession)
    fun velocityGenerateCode(
        clazz: KtClassOrObject,
        selectedMembers: List<KtNamedDeclaration>,
        contextMap: Map<String, Any?>,
        templateMacro: String?,
        useFullyQualifiedName: Boolean,
    ): String? {
        if (templateMacro == null) {
            return null
        }

        val sw = StringWriter()
        try {
            val vc = prepareContext(selectedMembers, clazz, useFullyQualifiedName, contextMap)

            // velocity
            val velocity = VelocityFactory.getVelocityEngine()
            velocity.evaluate(vc, sw, GenerateToStringWorker::class.java.getName(), templateMacro)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            throw GenerateCodeException("Error in Velocity code generator", e)
        }

        return StringUtil.convertLineSeparators(sw.buffer.toString())
    }

    context(_: KaSession)
    private fun prepareContext(
        selectedMembers: List<KtNamedDeclaration>,
        clazz: KtClassOrObject,
        useFullyQualifiedName: Boolean,
        contextMap: Map<String, Any?>
    ): VelocityContext {
        val vc = VelocityContext()

        //// field information
        val fieldElements = selectedMembers.mapNotNull { prop ->
            when (prop) {
                is KtProperty -> KotlinElementFactory.newFieldElement(prop)
                is KtParameter -> KotlinElementFactory.newFieldElement(prop)
                else -> null
            }
        }
        vc.put("fields", fieldElements)
        if (fieldElements.size == 1) {
            vc.put("field", fieldElements.get(0))
        }
        val ce = KotlinElementFactory.newClassElement(clazz)
        vc.put("class", ce)

        // information to keep as it is to avoid breaking compatibility with prior releases
        vc.put("classname", if (useFullyQualifiedName) ce.qualifiedName else ce.name)
        vc.put("FQClassname", ce.qualifiedName)
        vc.put(
            "classSignature",
            ce.name + (if (clazz.typeParameters.isNotEmpty())
                clazz.typeParameters.joinToString(prefix = "<", postfix = ">") { it.name!! }
            else ""))


        val project = clazz.project
        vc.put("project", project)

        vc.put("helper", GenerationHelper::class.java)
        vc.put("StringUtil", StringUtil::class.java)
        vc.put("NameUtil", NameUtil::class.java)
        vc.put("isCommon", clazz.platform.isCommon())
        vc.put("isJs", clazz.platform.isJs())
        vc.put("isNative", clazz.platform.isNative())
        vc.put("isWasm", clazz.platform.isWasm())

        for (paramName in contextMap.keys) {
            vc.put(paramName, contextMap[paramName])
        }
        return vc
    }

}