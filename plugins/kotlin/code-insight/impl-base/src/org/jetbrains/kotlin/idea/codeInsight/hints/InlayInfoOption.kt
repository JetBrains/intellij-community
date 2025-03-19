// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

sealed class InlayInfoOption

object NoInlayInfoOption: InlayInfoOption()

class NamedInlayInfoOption(val name: String): InlayInfoOption()

val SHOW_PROPERTY_TYPES = NamedInlayInfoOption("hints.type.property")
val SHOW_LOCAL_VARIABLE_TYPES = NamedInlayInfoOption("hints.type.variable")
val SHOW_FUNCTION_RETURN_TYPES = NamedInlayInfoOption("hints.type.function.return")
val SHOW_FUNCTION_PARAMETER_TYPES = NamedInlayInfoOption("hints.type.function.parameter")

val SHOW_RETURN_EXPRESSIONS = NamedInlayInfoOption("hints.lambda.return")
val SHOW_IMPLICIT_RECEIVERS_AND_PARAMS = NamedInlayInfoOption("hints.lambda.receivers.parameters")

val SHOW_BLACKLISTED_PARAMETERS = NamedInlayInfoOption("hints.parameters.blacklisted")
