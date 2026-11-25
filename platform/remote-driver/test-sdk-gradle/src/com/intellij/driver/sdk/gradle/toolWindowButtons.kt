package com.intellij.driver.sdk.gradle

import com.intellij.driver.sdk.ui.components.common.toolwindows.StripeButtonUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowRightToolbarUi

val ToolWindowRightToolbarUi.gradleButton: StripeButtonUi get() = stripeButton("Gradle")
