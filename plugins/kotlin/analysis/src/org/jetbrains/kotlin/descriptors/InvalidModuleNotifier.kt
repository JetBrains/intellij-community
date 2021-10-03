// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.descriptors

/**
 * TODO: Entire file has to be deleted during migration to Kotlin 1.6.0
 */
interface InvalidModuleNotifier {
    fun notifyModuleInvalidated(moduleDescriptor: ModuleDescriptor)
}

val INVALID_MODULE_NOTIFIER_CAPABILITY = ModuleCapability<InvalidModuleNotifier>("InvalidModuleNotifier")