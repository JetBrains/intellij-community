package com.intellij.diagnostic

val areComponentsInitialized get() = LoadingState.COMPONENTS_REGISTERED.isOccurred