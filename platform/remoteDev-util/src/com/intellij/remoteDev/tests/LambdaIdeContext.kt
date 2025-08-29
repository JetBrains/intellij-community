package com.intellij.remoteDev.tests

import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to all essential entities on this agent required to perform test operations
 */
@ApiStatus.Internal
interface LambdaIdeContext

@ApiStatus.Internal
interface LambdaMonolithContext: LambdaBackendContext, LambdaFrontendContext
@ApiStatus.Internal
interface LambdaBackendContext: LambdaIdeContext
@ApiStatus.Internal
interface LambdaFrontendContext: LambdaIdeContext

@ApiStatus.Internal
class LambdaBackendContextClass : LambdaBackendContext

@ApiStatus.Internal
class LambdaFrontendContextClass : LambdaFrontendContext

@ApiStatus.Internal
class LambdaMonolithContextClass : LambdaMonolithContext