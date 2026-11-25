package com.intellij.driver.sdk.clion

import com.intellij.driver.sdk.ui.components.common.toolwindows.StripeButtonUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowLeftToolbarUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowRightToolbarUi

val ToolWindowLeftToolbarUi.cmakeButton: StripeButtonUi get() = stripeButton("CMake")

val ToolWindowLeftToolbarUi.westButton: StripeButtonUi get() = stripeButton("West")

val ToolWindowLeftToolbarUi.vcpkg: StripeButtonUi get() = stripeButton("Vcpkg")

val ToolWindowRightToolbarUi.mesonButton: StripeButtonUi get() = stripeButton("Meson")
