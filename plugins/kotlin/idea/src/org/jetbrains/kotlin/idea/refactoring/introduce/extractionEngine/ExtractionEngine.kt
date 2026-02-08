// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.types.KotlinType

@K1Deprecation
abstract class ExtractionEngineHelper(@NlsContexts.DialogTitle operationName: String)
    : IExtractionEngineHelper<KotlinType, ExtractionData, ExtractionGeneratorConfiguration, ExtractionResult, ExtractableCodeDescriptor, ExtractableCodeDescriptorWithConflicts>(operationName) {

    override fun generateDeclaration(config: ExtractionGeneratorConfiguration): ExtractionResult {
        return config.generateDeclaration()
    }

    override fun validate(descriptor: ExtractableCodeDescriptor): ExtractableCodeDescriptorWithConflicts = descriptor.validate()
}

@K1Deprecation
class ExtractionEngine(
    helper: ExtractionEngineHelper
): IExtractionEngine<KotlinType, ExtractionData, ExtractionGeneratorConfiguration, ExtractionResult, ExtractableCodeDescriptor, ExtractableCodeDescriptorWithConflicts>(helper) {
    override fun performAnalysis(extractionData: ExtractionData): AnalysisResult<KotlinType> {
        return extractionData.performAnalysis()
    }
}
