// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Resource is something that could be created, used and destroyed. Examples are project and module.
 * For most resources leakage is checked by {@link com.intellij.testFramework.junit5.impl.TestApplicationLeakTrackerExtension} which is included in many annotations,
 * and should always be used.
 * Resources are created by {@link com.intellij.testFramework.junit5.resources.providers.ResourceProvider}.
 * Some providers accept parameters to create resources, hence {@link com.intellij.testFramework.junit5.resources.providers.ParameterizableResourceProvider}
 * <br/>
 * Providers are installed using JUnit5 machinery. When installed, resources are injected into fields, test method arguments and can also be
 * created explicitly with {@link com.intellij.testFramework.junit5.resources.ResourceExtensionApiKt#create(com.intellij.testFramework.junit5.resources.ResourceExtensionApi, boolean, kotlin.coroutines.Continuation)}
 * <br/>
 * The easy start is to use {@link com.intellij.testFramework.junit5.resources.FullApplication} and get modules, projects, disposables etc.
 * To customize resource, take provider {@link com.intellij.testFramework.junit5.resources.providers}, parametrize it, and use
 * methods from {@link com.intellij.testFramework.junit5.resources.UserApiKt} or {@link com.intellij.testFramework.junit5.resources.ResourceExtensionApiKt},
 * along with {@link org.junit.jupiter.api.extension.RegisterExtension}.
 * <br/>
 * Resources have class lifespan, hence one resource used by all methods.
 * To create a resource with method lifespan, use {@link org.junit.jupiter.api.extension.RegisterExtension} with instance field.
 * <br/>
 * See {@link com.intellij.testFramework.junit5.resources.UserApiKt} for top-level API.
 * See {@link com.intellij.testFramework.junit5.showcase.resources} for usage examples
 */
@ApiStatus.Internal
package com.intellij.testFramework.junit5.resources;

import org.jetbrains.annotations.ApiStatus;