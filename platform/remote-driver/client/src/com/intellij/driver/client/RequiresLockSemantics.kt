package com.intellij.driver.client

import com.intellij.driver.model.LockSemantics

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class RequiresLockSemantics(val lockSemantics: LockSemantics)
