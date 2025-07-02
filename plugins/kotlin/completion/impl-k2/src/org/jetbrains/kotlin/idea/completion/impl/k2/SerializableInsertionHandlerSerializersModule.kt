// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.completion.KeywordCompletion
import org.jetbrains.kotlin.idea.completion.KeywordLookupObject
import org.jetbrains.kotlin.idea.completion.NameWithTypeLookupElementDecoratorInsertHandler
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableLookupObject
import org.jetbrains.kotlin.idea.completion.contributors.helpers.SuperCallInsertionHandler
import org.jetbrains.kotlin.idea.completion.contributors.keywords.SuperKeywordLookupObject
import org.jetbrains.kotlin.idea.completion.handlers.KeywordConstructLookupObject
import org.jetbrains.kotlin.idea.completion.handlers.KeywordConstructorInsertionHandler
import org.jetbrains.kotlin.idea.completion.handlers.LineAdjusterInsertionHandler
import org.jetbrains.kotlin.idea.completion.handlers.WithTailInsertHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.*
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.BracketOperatorInsertionHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.TrailingLambdaInsertionHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentLookupObject
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentWithValueInsertionHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.TypeInsertHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.TypeLookupObject
import org.jetbrains.kotlin.idea.completion.implCommon.handlers.CompletionCharInsertHandler
import org.jetbrains.kotlin.idea.completion.implCommon.handlers.NamedArgumentInsertHandler
import org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates.InsertStringTemplateBracesInsertHandler
import org.jetbrains.kotlin.idea.completion.lookups.QuotedNamesAwareInsertionHandler
import org.jetbrains.kotlin.idea.completion.lookups.UniqueLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.UpdateLookupElementBuilderToInsertTypeQualifierOnSuperInsertionHandler
import org.jetbrains.kotlin.idea.completion.lookups.factories.*

@ApiStatus.Internal
val serializableInsertionHandlerSerializersModule: SerializersModule = SerializersModule {
    polymorphic(SerializableLookupObject::class) {
        subclass(ClassifierLookupObject::class, ClassifierLookupObject.serializer())
        subclass(TypeParameterInWhenClauseILookupObject::class, TypeParameterInWhenClauseILookupObject.serializer())
        subclass(WhenConditionLookupObject::class, WhenConditionLookupObject.serializer())
        subclass(FunctionCallLookupObject::class, FunctionCallLookupObject.serializer())
        subclass(NamedArgumentLookupObject::class, NamedArgumentLookupObject.serializer())
        subclass(OperatorNameLookupObject::class, OperatorNameLookupObject.serializer())
        subclass(PackagePartLookupObject::class, PackagePartLookupObject.serializer())
        subclass(VariableLookupObject::class, VariableLookupObject.serializer())

        subclass(KeywordLookupObject::class, KeywordLookupObject.serializer())
        subclass(KeywordConstructLookupObject::class, KeywordConstructLookupObject.serializer())
        subclass(SuperKeywordLookupObject::class, SuperKeywordLookupObject.serializer())
        subclass(TypeLookupObject::class, TypeLookupObject.serializer())
        subclass(UniqueLookupObject::class, UniqueLookupObject.serializer())
        subclass(SuperLookupObject::class, SuperLookupObject.serializer())
    }

    polymorphic(SerializableInsertHandler::class) {
        subclass(NamedArgumentInsertHandler::class, NamedArgumentInsertHandler.serializer())
        subclass(BracketOperatorInsertionHandler::class, BracketOperatorInsertionHandler.serializer())
        subclass(TypeInsertHandler::class, TypeInsertHandler.serializer())
        subclass(InsertStringTemplateBracesInsertHandler::class, InsertStringTemplateBracesInsertHandler.serializer())
        subclass(KeywordCompletion.UseSiteAnnotationTargetInsertHandler::class, KeywordCompletion.UseSiteAnnotationTargetInsertHandler.serializer())
        subclass(KeywordCompletion.SpaceAfterInsertHandler::class, KeywordCompletion.SpaceAfterInsertHandler.serializer())
        subclass(SuperCallInsertionHandler::class, SuperCallInsertionHandler.serializer())
        subclass(PackagePartInsertionHandler::class, PackagePartInsertionHandler.serializer())
        subclass(QuotedNamesAwareInsertionHandler::class, QuotedNamesAwareInsertionHandler.serializer())
        subclass(NamedArgumentInsertHandler::class, NamedArgumentInsertHandler.serializer())
        subclass(TypeParameterInWhenClauseInsertionHandler::class, TypeParameterInWhenClauseInsertionHandler.serializer())
        subclass(FunctionInsertionHandler::class, FunctionInsertionHandler.serializer())
        subclass(VariableInsertionHandler::class, VariableInsertionHandler.serializer())
        subclass(CallableIdentifierInsertionHandler::class, CallableIdentifierInsertionHandler.serializer())
        subclass(ClassifierInsertionHandler::class, ClassifierInsertionHandler.serializer())
        subclass(WithCallArgsInsertionHandler::class, WithCallArgsInsertionHandler.serializer())
        subclass(AsIdentifierCustomInsertionHandler::class, AsIdentifierCustomInsertionHandler.serializer())
        subclass(WhenConditionInsertionHandler::class, WhenConditionInsertionHandler.serializer())
        subclass(WrapSingleStringTemplateEntryWithBracesInsertHandler::class, WrapSingleStringTemplateEntryWithBracesInsertHandler.serializer())
        subclass(CompletionCharInsertHandler::class, CompletionCharInsertHandler.serializer())
        subclass(LineAdjusterInsertionHandler::class, LineAdjusterInsertionHandler.serializer())
        subclass(KeywordConstructorInsertionHandler::class, KeywordConstructorInsertionHandler.serializer())
        subclass(WithTailInsertHandler::class, WithTailInsertHandler.serializer())
        subclass(ChainedInsertHandler::class, ChainedInsertHandler.serializer())
        subclass(TailTextInsertHandler::class, TailTextInsertHandler.serializer())
        subclass(CompoundInsertionHandler::class, CompoundInsertionHandler.serializer())
        subclass(UpdateLookupElementBuilderToInsertTypeQualifierOnSuperInsertionHandler::class, UpdateLookupElementBuilderToInsertTypeQualifierOnSuperInsertionHandler.serializer())
        subclass(FirCompletionContributorBase.AdaptToExplicitReceiverInsertionHandler::class, FirCompletionContributorBase.AdaptToExplicitReceiverInsertionHandler.serializer())
        subclass(FirTrailingFunctionParameterNameCompletionContributorBase.WithImportInsertionHandler::class, FirTrailingFunctionParameterNameCompletionContributorBase.WithImportInsertionHandler.serializer())
        subclass(NameWithTypeLookupElementDecoratorInsertHandler::class, NameWithTypeLookupElementDecoratorInsertHandler.serializer())
        subclass(NamedArgumentWithValueInsertionHandler::class, NamedArgumentWithValueInsertionHandler.serializer())
        subclass(TrailingLambdaInsertionHandler::class, serializer = TrailingLambdaInsertionHandler.serializer())
    }
}
