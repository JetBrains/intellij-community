// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/layer-locality.spec.md

import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Tells the storage locality of one layer: which of two boundaries its values may cross. The first is the VCS
 * boundary: committed and shared, or kept out of the repository. The second is the machine boundary: the value may
 * cross the remote-development split between the frontend and the backend, or it stays on one machine. The three
 * levels form a scale of reach.
 *
 * A layer has exactly **one** locality. Within one context, the layers resolve by
 * **[TEAM] < [LOCAL] < [ENVIRONMENTAL]**. So the machine value overrides the personal override, and the personal
 * override overrides the shared default (see [byResolutionPrecedence]). The provider order only breaks a tie within
 * one locality.
 *
 * @see KeyLocality for the key-side policy that decides which localities a key may live in.
 */
@Experimental
enum class LayerLocality {
  /** Committed to VCS and shared across machines. It holds the shared team default. */
  TEAM,

  /**
   * Kept out of VCS for one user, but it still crosses the remote-development split between the frontend and the
   * backend. It holds a personal override.
   */
  LOCAL,

  /** Bound to the machine, and it never leaves it. An example is an absolute local path, which has no meaning elsewhere. */
  ENVIRONMENTAL,
  ;

  @Experimental
  companion object {
    /**
     * Holds the localities from the highest to the lowest resolution precedence: `ENVIRONMENTAL`, `LOCAL`, `TEAM`. A
     * read folds the layers in this order, so a more specific locality overrides a wider one. A narrower context
     * overrides a wider one in the same way. The order lives here, not in the declaration order of the enum.
     */
    val byResolutionPrecedence: List<LayerLocality> = listOf(ENVIRONMENTAL, LOCAL, TEAM)
  }
}

/**
 * Tells the locality policy of a key: which [LayerLocality] layers it may live in, and where a write lands by
 * default. A layer *is* a locality, and a key *constrains* which localities it accepts. So the two are separate
 * types, and the compatibility relation lives here.
 *
 * The relation makes an illegal combination impossible to declare. There is no "team-or-environmental" key, because
 * [ENVIRONMENTAL] is exclusive on both sides: a machine-bound path can only live in the machine layer.
 */
@Experimental
enum class KeyLocality(
  /** Holds the locality where a plain write of a key with this policy lands, when the caller does not say otherwise. */
  val defaultLayer: LayerLocality,
  private val accepted: Set<LayerLocality>,
) {
  /** Team or local: the ordinary key. A write defaults to [LayerLocality.LOCAL]. A write to the team layer is an explicit action. */
  SHAREABLE(LayerLocality.LOCAL, setOf(LayerLocality.TEAM, LayerLocality.LOCAL)),

  /** Environmental only: a machine-bound value, such as a path. A read and a write never touch the team or local layers. */
  ENVIRONMENTAL(LayerLocality.ENVIRONMENTAL, setOf(LayerLocality.ENVIRONMENTAL)),
  ;

  /** Returns whether a key with this policy may resolve from, and be written to, a layer of the [layer] locality. */
  fun accepts(layer: LayerLocality): Boolean = layer in accepted

  /**
   * Lists the localities a write tries at each context, most-preferred first: the [defaultLayer], then the other
   * accepted ones. The context is the outer axis: a write tries every locality at one context before it moves to a
   * wider one. So a `SHAREABLE` write lands in a `TEAM` layer at a narrower context before a `LOCAL` layer at a
   * wider one.
   */
  val writePreference: List<LayerLocality> = listOf(defaultLayer) + accepted.filter { it != defaultLayer }
}

/**
 * Holds the per-machine **team-editing mode** (ADR 0076). While it is `true`, a plain write of a
 * [KeyLocality.SHAREABLE] key tries the `team` layer first, not the `local` one. So a user turns the mode on once,
 * then edits the shared team settings directly. The key is itself [KeyLocality.ENVIRONMENTAL], so the mode is bound
 * to the machine and is never committed or shared. Only the application and the project contexts accept it.
 */
@Experimental
val EDIT_TEAM_LAYER: SettingsKey<Boolean> = SettingsKey(
  name = "settings.editTeamLayer",
  default = false,
  locality = KeyLocality.ENVIRONMENTAL,
  contextFilter = { it.kind == SettingsContextKind.APPLICATION || it.kind == SettingsContextKind.PROJECT },
)
