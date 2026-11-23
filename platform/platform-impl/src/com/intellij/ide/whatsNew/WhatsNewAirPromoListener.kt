// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.whatsNew

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * The facts of the IJ Air block on the What's new page, for the Air plugin. The platform cannot see Air, so Air reports
 * them in its own FUS group.
 */
@ApiStatus.Internal
interface WhatsNewAirPromoListener {
  /** The page opens with the IJ Air block enabled. It is sent before the editor opens. */
  fun blockShown(page: WhatsNewAirPromoPage)

  /** The user clicked the button of the IJ Air block. It is sent before the button acts. */
  fun blockClicked(page: WhatsNewAirPromoPage)

  /** The platform requests the tooltip on the Agent Sessions tool window after a project opens from the welcome screen. */
  fun tooltipShown(project: Project)

  companion object {
    /**
     * The AI Assistant window state before the migration collapsed it. The first page with known state consumes it and writes false.
     * Access uses this key as the lock. A false value also prevents a later collapse from restoring the snapshot.
     */
    @JvmField
    val AI_ASSISTANT_OPEN_BEFORE_COLLAPSE: Key<Boolean> = Key.create("whats.new.air.promo.aia.open.before.collapse")

    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<WhatsNewAirPromoListener> = Topic(WhatsNewAirPromoListener::class.java, Topic.BroadcastDirection.NONE)
  }
}

/** The audience context of one opening of the What's new page. */
@ApiStatus.Internal
class WhatsNewAirPromoPage(
  val project: Project,
  val welcomeScreenProject: Boolean = WelcomeScreenProjectProvider.isWelcomeScreenProject(project),
) : UserDataHolderBase() {
  companion object {
    @JvmField
    val DATA_KEY: DataKey<WhatsNewAirPromoPage> = DataKey.create("whats.new.air.promo.page")
  }
}
