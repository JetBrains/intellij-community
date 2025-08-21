package com.intellij.remoteDev.tests

import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to all essential entities on this agent required to perform test operations
 */
interface LambdaIdeContext

@ApiStatus.Internal
class LambdaBackendContext() : LambdaIdeContext

@ApiStatus.Internal
class LambdaFrontendContext() : LambdaIdeContext