// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Classes in this package are used to provide information about the modular structure of a product based on the IntelliJ Platform at 
 * runtime. They provide an abstraction over actual layout of file on disk, and allow writing code which will work in different modes 
 * (running IDE or tests from sources, running IDE from production distribution, running IDE or tests packaged in separate Maven artifacts, 
 * etc). 
 * 
 * <p>
 * Files containing information about modules, their resources and dependencies are generated during compilation by
 * {@link com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuilder} (currently it's controlled by a registry
 * option disabled by default). The exact format of these files is an implementation detail, it may and will be changed in the future.
 * <br>
 * When build scripts produce archives 
 * containing distribution of the product, they will also generate an updated version of these files which will point to actual locations
 * of JAR files corresponding to modules inside the distribution (this part isn't implemented yet).
 * <br>
 * Code which configures classloaders and access resources at runtime will use information from 
 * {@link com.intellij.platform.runtime.repository.RuntimeModuleRepository the repository} (this part isn't implemented yet).  
 * </p>
 * 
 * <p>
 * All classes in this package <strong>are experimental</strong> and their API may change in future versions.
 * </p>
 */
@ApiStatus.Experimental
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.ApiStatus;