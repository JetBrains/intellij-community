package com.intellij.driver.sdk.maven

import com.intellij.driver.sdk.ui.components.common.toolwindows.StripeButtonUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowRightToolbarUi

val ToolWindowRightToolbarUi.mavenButton: StripeButtonUi get() = stripeButton("Maven")
