// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.slicer

import com.intellij.slicer.SliceUsage
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.descriptorUtil.isCompanionObject
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension

object KotlinSliceUsageSuffix {
  private val descriptorRenderer = DescriptorRenderer.ONLY_NAMES_WITH_SHORT_TYPES.withOptions {
    withoutReturnType = true
    renderConstructorKeyword = false
    valueParametersHandler = TruncatedValueParametersHandler(maxParameters = 2)
  }

  @Nls
  fun containerSuffix(sliceUsage: SliceUsage): String? {
    val element = sliceUsage.element ?: return null
    var declaration = element.parents.firstOrNull {
      it is KtClass ||
      it is KtObjectDeclaration && !it.isObjectLiteral() ||
      it is KtNamedFunction && !it.isLocal ||
      it is KtProperty && !it.isLocal ||
      it is KtPropertyAccessor ||
      it is KtConstructor<*>
    } as? KtDeclaration ?: return null

    // for a val or var among primary constructor parameters show the class as container
    if (declaration is KtPrimaryConstructor && element is KtParameter && element.hasValOrVar()) {
      declaration = declaration.containingClassOrObject!!
    }

    @Suppress("HardCodedStringLiteral")
    return buildString {
      append(KotlinBundle.message("slicer.text.in", ""))
      append(" ")

      val descriptor = declaration.unsafeResolveToDescriptor()

      if (!descriptor.isExtension && descriptor !is ConstructorDescriptor && !descriptor.isCompanionObject()) {
        val containingClassifier = descriptor.containingDeclaration as? ClassifierDescriptor
        if (containingClassifier != null) {
          append(descriptorRenderer.render(containingClassifier))
          append(".")
        }
      }

      when (descriptor) {
        is PropertyDescriptor -> {
          renderPropertyOrAccessor(descriptor)
        }

        is PropertyAccessorDescriptor -> {
          val property = descriptor.correspondingProperty
          renderPropertyOrAccessor(property, if (descriptor is PropertyGetterDescriptor) ".get" else ".set")
        }

        else -> {
          append(descriptorRenderer.render(descriptor))
        }
      }
    }
  }

  private fun StringBuilder.renderPropertyOrAccessor(propertyDescriptor: PropertyDescriptor, accessorSuffix: String = "") {
    append(propertyDescriptor.name.render())
    append(accessorSuffix)
    val receiverType = propertyDescriptor.extensionReceiverParameter?.type
    if (receiverType != null) {
      append(" on ")
      append(descriptorRenderer.renderType(receiverType))
    }
  }

  private class TruncatedValueParametersHandler(private val maxParameters: Int) : DescriptorRenderer.ValueParametersHandler {
    private var truncateLength = -1

    override fun appendBeforeValueParameters(parameterCount: Int, builder: StringBuilder) {
      builder.append("(")
    }

    override fun appendAfterValueParameters(parameterCount: Int, builder: StringBuilder) {
      if (parameterCount > maxParameters) {
        builder.setLength(truncateLength)
        builder.append(",${Typography.ellipsis}")
      }
      builder.append(")")
    }

    override fun appendBeforeValueParameter(
      parameter: ValueParameterDescriptor,
      parameterIndex: Int,
      parameterCount: Int,
      builder: StringBuilder
    ) {
    }

    override fun appendAfterValueParameter(
      parameter: ValueParameterDescriptor,
      parameterIndex: Int,
      parameterCount: Int,
      builder: StringBuilder
    ) {
      if (parameterIndex < parameterCount - 1) {
        if (parameterIndex == maxParameters - 1) {
          truncateLength = builder.length
        } else {
          builder.append(", ")
        }
      }
    }
  }

}