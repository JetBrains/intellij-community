package com.intellij.cce.interpreter

interface InvokersFactory {
  fun createActionsInvoker(): ActionsInvoker
  fun createFeatureInvoker(): FeatureInvoker
}
