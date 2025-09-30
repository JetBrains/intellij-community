// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup

/**
 * Marker interface indicating that the popup should be rendered on the backend side in the split mode (RemDev).
 * Used for shiny popups that are too complex to be rendered on the frontend side.
 */
interface BackendRenderedPopup