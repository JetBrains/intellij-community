// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import org.jdom.Element
import org.jetbrains.annotations.ApiStatus


/**
 * Marker object to ease migration from the [PersistentStateComponent] to the WSM. Persistent state component may return [HandledByWSM] from
 * its [PersistentStateComponent.getState] implementation as an indication that state exists, but is handled externally (by the WSM).
 *
 * The main use case is the following: depending on a registry flag, a component may handle its state by itself (and then return the actual
 * state) or delegate state handling to the WSM, and then return this object to make sure that the state set by the WSM is not overwritten.
 **/
@ApiStatus.Internal
public val HandledByWSM: Element = Element("__HandledByWSM__")