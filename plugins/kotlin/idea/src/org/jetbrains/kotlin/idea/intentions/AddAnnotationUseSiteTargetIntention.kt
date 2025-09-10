// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.addOrChooseUseSiteTarget
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.applicableUseSiteTargets
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class AddAnnotationUseSiteTargetIntention : SelfTargetingIntention<KtAnnotationEntry>(
    KtAnnotationEntry::class.java,
    KotlinBundle.messagePointer("add.use.site.target")
) {

    override fun isApplicableTo(element: KtAnnotationEntry, caretOffset: Int): Boolean {
        val useSiteTargets = element.getApplicableUseSiteTargets()
        if (useSiteTargets.isEmpty()) return false
        if (useSiteTargets.size == 1) {
            setTextGetter(KotlinBundle.messagePointer("text.add.use.site.target.0", useSiteTargets.first().renderName))
        } else {
            setTextGetter(KotlinBundle.messagePointer("add.use.site.target"))
        }
        return true
    }

    override fun applyTo(element: KtAnnotationEntry, editor: Editor?) {
        val useSiteTargets = element.getApplicableUseSiteTargets()
        element.addOrChooseUseSiteTarget(useSiteTargets, editor)
    }
}

fun KtAnnotationEntry.getApplicableUseSiteTargets(): List<AnnotationUseSiteTarget> {
    val context = analyze(BodyResolveMode.PARTIAL)
    val descriptor = context[BindingContext.ANNOTATION, this]
    val applicableTargets = descriptor?.let { AnnotationChecker.applicableTargetSet(descriptor) }.orEmpty()
    return applicableUseSiteTargets(applicableTargets)
}
