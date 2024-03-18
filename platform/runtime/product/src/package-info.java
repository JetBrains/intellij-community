// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Classes in this package are used to provide information about how the product based on IntelliJ Platform is structured.
 * {@link com.intellij.platform.runtime.product.ProductModules ProductModules} describes which modules are included in the main part, and 
 * which belong to plugins. 
 * {@link com.intellij.platform.runtime.product.ProductMode ProductMode} specifies in which mode the product can be started.
 * 
 * <p>
 * All classes in this package <strong>are experimental</strong> and their API may change in future versions.
 * </p>
 */
@ApiStatus.Experimental
package com.intellij.platform.runtime.product;

import org.jetbrains.annotations.ApiStatus;