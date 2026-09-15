// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import java.io.Closeable;
import java.io.Flushable;

/// Trait: an entity has some persistent storage behind it;
/// It and could be [flush]-ed, and must be [close]-ed.
public interface Durable extends Closeable, Flushable {
}
