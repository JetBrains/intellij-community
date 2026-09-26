package com.intellij.execution.multilaunch.design.popups

interface SelectorPopupsContainer {
  fun getSelectorPopupProviders(): List<SelectorPopupProvider>
}