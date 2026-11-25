package com.intellij.driver.sdk.ultimate

import com.intellij.driver.sdk.ui.components.common.toolwindows.StripeButtonUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowLeftToolbarUi

val ToolWindowLeftToolbarUi.jpaButton: StripeButtonUi get() = stripeButton("JPA Console")

val ToolWindowLeftToolbarUi.persistenceButton: StripeButtonUi get() = stripeButton("Persistence")
