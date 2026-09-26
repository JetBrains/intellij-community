// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.codeInsight.daemon.GutterIconDescriptor
import com.intellij.icons.AllIcons
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

object KotlinLineMarkerOptions {
    val overriddenOption: GutterIconDescriptor.Option = GutterIconDescriptor.Option(
        "kotlin.overridden",
        KotlinBundle.message("highlighter.name.overridden.declaration"), AllIcons.Gutter.OverridenMethod
    )

    val implementedOption: GutterIconDescriptor.Option = GutterIconDescriptor.Option(
        "kotlin.implemented",
        KotlinBundle.message("highlighter.name.implemented.declaration"), AllIcons.Gutter.ImplementedMethod
    )

    val overridingOption: GutterIconDescriptor.Option = GutterIconDescriptor.Option(
        "kotlin.overriding",
        KotlinBundle.message("highlighter.name.overriding.declaration"), AllIcons.Gutter.OverridingMethod
    )

    val implementingOption: GutterIconDescriptor.Option =
        GutterIconDescriptor.Option(
            "kotlin.implementing",
            KotlinBundle.message("highlighter.name.implementing.declaration"),
            AllIcons.Gutter.ImplementingMethod
        )

    val actualOption: GutterIconDescriptor.Option = GutterIconDescriptor.Option(
        "kotlin.actual",
        KotlinBundle.message("highlighter.name.multiplatform.actual.declaration"), KotlinIcons.ACTUAL
    )

    val expectOption: GutterIconDescriptor.Option = GutterIconDescriptor.Option(
        "kotlin.expect",
        KotlinBundle.message("highlighter.name.multiplatform.expect.declaration"), KotlinIcons.EXPECT
    )

    val dslOption: GutterIconDescriptor.Option =
        GutterIconDescriptor.Option("kotlin.dsl", KotlinBundle.message("highlighter.name.dsl.markers"), KotlinIcons.DSL_MARKER_ANNOTATION)

    val suspendCallOption: GutterIconDescriptor.Option =
        GutterIconDescriptor.Option("kotlin.suspend.call",
            KotlinBundle.message("highlighter.tool.tip.text.suspend.call"),
            KotlinIcons.SUSPEND_CALL
        )

    val recursiveOption: GutterIconDescriptor.Option =
        GutterIconDescriptor.Option("kotlin.recursive", 
                                    KotlinBundle.message("highlighter.tool.tip.text.recursive.call"), 
                                    AllIcons.Gutter.RecursiveMethod)

    val options: Array<GutterIconDescriptor.Option> = arrayOf(
        overriddenOption, implementedOption,
        overridingOption, implementingOption,
        actualOption, expectOption,
        dslOption,
        suspendCallOption,
        recursiveOption
    )
}