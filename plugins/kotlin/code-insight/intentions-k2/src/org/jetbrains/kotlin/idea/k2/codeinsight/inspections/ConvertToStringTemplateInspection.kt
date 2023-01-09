// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ConvertToStringTemplateIntention
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.canConvertToStringTemplate
import org.jetbrains.kotlin.psi.KtBinaryExpression

class ConvertToStringTemplateInspection : IntentionBasedInspection<KtBinaryExpression>(
  ConvertToStringTemplateIntention::class,
  ::canConvertToStringTemplate,
  problemText = KotlinBundle.message("convert.concatenation.to.template.before.text")
)