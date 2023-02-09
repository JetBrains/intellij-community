package com.intellij.xdebugger.impl

import com.intellij.xdebugger.frame.XFullValueEvaluator
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
abstract class CustomComponentEvaluator(name: String) : XFullValueEvaluator() {
  open fun createComponent(fullValue: String?) : JComponent? =  null;
}