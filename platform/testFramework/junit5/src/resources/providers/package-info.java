// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * {@link com.intellij.testFramework.junit5.resources.providers.ResourceProvider}s for various resources.
 * If you are looking for provider to use, just choose one, and pass it to {@link com.intellij.testFramework.junit5.resources.ResourceExtensionApiKt}.
 * <br/>
 * To implement your own provider, extend either {@link com.intellij.testFramework.junit5.resources.providers.ResourceProvider}
 * or {@link com.intellij.testFramework.junit5.resources.providers.ParameterizableResourceProvider} if resource creation could be customized.
 * You still need to support parameterless creation for field/parameter injection.
 * If resource uses {@link java.nio.file.Path} or {@link java.nio.file.FileSystem}, consider using {@link com.intellij.testFramework.junit5.resources.providers.PathInfo}
 * as {@link com.intellij.testFramework.junit5.resources.providers.PathInfo} extension method, so path would be cleaned up automatically.
 * If you need resources from other providers, use {@link com.intellij.testFramework.junit5.resources.providers.ResourceStorage}
 */
@ApiStatus.Internal
package com.intellij.testFramework.junit5.resources.providers;

import org.jetbrains.annotations.ApiStatus;