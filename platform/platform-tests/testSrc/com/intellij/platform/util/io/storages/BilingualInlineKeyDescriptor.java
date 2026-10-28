// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.util.io.InlineKeyDescriptor;

/** Resolves the inherited final methods in Java so Kotlin tests can use both descriptor APIs */
abstract class BilingualInlineKeyDescriptor<K> extends InlineKeyDescriptor<K> implements InlineKeyDescriptorEx<K> {
}
