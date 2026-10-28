// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/layer-loading.spec.md
// @spec ../../../../../../spec/layer-resolution.spec.md
// @spec ../../../../../../spec/settings-writes.spec.md

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Connects the platform to one layer of the settings. The vault keeps its model as
 * `context -> (locality, provider) -> data`, and a provider is the provider part of that address. The **vault owns
 * the model and all the mutation logic** (ADR 0057). A provider is only a **persistence port**. It loads its layer,
 * declares which keys it holds and where it takes a write, and serializes what the vault hands back:
 *
 * - [requestData] feeds the layer *in* on a **load that the vault drives**. The vault calls it and installs what it
 *   returns. [SettingsVault.push] feeds a **spontaneous** update in, such as an external edit of a file that the
 *   provider watches. In both paths, an empty [LayerData] removes the entry for its context.
 * - [acceptsKey] filters the keys this provider holds, on a read and on a write. [availableLocalities] declares
 *   which localities take a write at a context. The vault owns the locality match. It picks the locality and changes
 *   the model in the first layer that offers it, and it never touches a layer that offers none (ADR 0129).
 * - [writeData] persists the layer *out*. The vault reads the current slice of its model and asks the provider to
 *   serialize it. The provider does no read-modify-write of its own (ADR 0133).
 * - [contextKindRange] declares the range of context kinds that this provider supplies layers at. The vault drops a
 *   layer outside it (ADR 0159).
 *
 * As an [EP_NAME] extension, a provider holds **no mutable state**. The layer data belongs to a service that the
 * provider delegates to.
 *
 * The standard `id` and `order` extension attributes order the providers, for example
 * `order="after settings.v2.projectProvider"`. The vault reads the sorted `extensionList` of [EP_NAME]. The two base
 * layers of the platform come first: `settings.v2.appDefaults`, then `settings.v2.appProvider`. A provider that sorts
 * ahead of either of them is a runtime error. `SettingsVaultImpl.assertBaseLayerOrder` checks it. Anchor a provider
 * after `settings.v2.appProvider` or later.
 */
@Experimental
interface LayerProvider {
  /**
   * Computes and **returns** the layers of this provider for [scopeContext], which the vault installs. The scope is a
   * root that covers all its subcontexts: the application, a project, or a directory and everything under it. So the
   * provider returns one [LayerData] for each relevant context in it. An empty one clears the entry of its context.
   * The vault also removes a layer under [scopeContext] that this provider no longer returns (ADR 0065).
   *
   * The vault calls it on a [SettingsVault.reload], at startup and at a project open, and when this provider
   * registers. A provider that watches its own store sends a later change through [SettingsVault.push] instead. The
   * default returns nothing: the layer has no data until something pushes it.
   *
   * @param scopeContext the root of the scope that the vault loads
   * @return the layers of this provider in the scope
   */
  suspend fun requestData(scopeContext: SettingsContext): List<LayerData> = emptyList()

  /**
   * Holds the range of context kinds that this provider supplies layers at (ADR 0159). The vault reads it before the
   * provider answers. The vault logs an error for a layer outside this range and drops it. This applies to a layer
   * that [requestData] returns and to a layer that [SettingsVault.push] gets. The default covers every kind.
   *
   * On a reload, the contexts of every kind in the range wait for this provider. A context of a narrower kind waits
   * too, because a context gets ready only after every wider bucket has installed (ADR 0160). So a slow provider must
   * declare a narrow range at the narrowest kinds that it can. A slow provider with a wide range delays every context
   * that it covers.
   */
  val contextKindRange: SettingsContextKindRange get() = SettingsContextKindRange.EVERY_KIND

  /**
   * Returns whether this provider holds [key]. A read and a write both consult it. For a key that it rejects, the
   * layers of this provider do not exist, the same as for a key whose [layer filter][SettingsKey.layerFilter]
   * rejects the provider (ADR 0129). **Synchronous and pure**, because the vault calls it on every read. The default
   * accepts every key.
   *
   * @param key the key that a read resolves or a write stores
   * @return `true` when this provider may hold a value for [key]
   */
  fun acceptsKey(key: SettingsKey<*>): Boolean = true

  /**
   * Returns the localities that take a write at [context]. This is the only write check on the context (ADR 0129).
   * An empty set means that the provider takes no write there. A read-only layer and a context kind that the layer
   * does not store both give an empty set. The vault owns the locality match. It picks a locality by the
   * [KeyLocality][SettingsKey.locality] preference of the key, then writes only where this set allows it. A provider
   * never decides the locality itself. It only declares what it has. **Synchronous and pure**, because the vault
   * calls it when it plans a write, and under its write lock for a slot operation. The default is none: a provider
   * that takes no write.
   *
   * @param context the context that a write tries
   * @return the localities that take a write at [context], or an empty set
   */
  fun availableLocalities(context: SettingsContext): Set<LayerLocality> = emptySet()

  /**
   * Returns the context that owns the physical store of a write at [context]. The vault persists one such store as a
   * unit. By default it is the [real context][SettingsContext.realContext] itself. A store can back several contexts,
   * for example a directory config that also holds the overrides of its files. Then the provider maps all of them to
   * the one owning context, and the vault groups their slices into one [writeData].
   *
   * It must be cheap, because the vault calls it under its write lock. It may read the disk, so its result can change
   * when the store changes on disk.
   *
   * @param context the context that a write or a reload covers
   * @return the context that owns the store
   */
  fun storageContext(context: SettingsContext): SettingsContext = context.realContext

  /**
   * Persists what the vault holds for the [locality] layer of [storageContext]. The provider only **serializes what
   * it gets**, with no read-modify-write, because the vault has already applied every mutation (ADR 0133). Each
   * locality persists to its own physical store. The vault calls it out of its write lock, after a debounce delay or
   * at once on a [SettingsVault.flush]. The default does nothing, for a layer that does not persist.
   *
   * @param storageContext the owning context of the store, as [storageContext] returns it
   * @param locality the locality of the layer to persist
   * @param slices the whole layer [MapValue] of each context that stores in this unit. A plain layer has one entry.
   *   A store that backs several contexts has an entry for the directory and for each file.
   * @return [SaveResult.SAVED] when the store holds [slices], or [SaveResult.DROPPED] when the provider could not
   *   store them, for example because [storageContext] does not exist. On a drop, the vault removes every layer of
   *   the store from its model (ADR 0150).
   */
  suspend fun writeData(storageContext: SettingsContext, locality: LayerLocality, slices: Map<SettingsContext, MapValue>): SaveResult =
    SaveResult.SAVED

  @Experimental
  companion object {
    val EP_NAME: ExtensionPointName<LayerProvider> =
      ExtensionPointName("com.intellij.settings.v2.layerProvider")
  }
}

/**
 * Holds a range of context kinds, from [widestKind] to [narrowestKind]. The entries of [SettingsContextKind] go from
 * the widest kind to the narrowest kind. A [LayerProvider] declares its range in [LayerProvider.contextKindRange].
 *
 * @property widestKind the widest kind in the range
 * @property narrowestKind the narrowest kind in the range, or `null` when the range is open, because the provider
 *   cannot tell its narrowest kind
 */
@Experimental
class SettingsContextKindRange(val widestKind: SettingsContextKind, val narrowestKind: SettingsContextKind?) {
  init {
    require(narrowestKind == null || narrowestKind >= widestKind) {
      "The narrowest kind $narrowestKind is wider than the widest kind $widestKind"
    }
  }

  /**
   * Returns whether [kind] is in this range.
   *
   * @param kind the kind of a context
   * @return `true` when [kind] is not wider than [widestKind], and not narrower than [narrowestKind] when the range is
   *   not open
   */
  operator fun contains(kind: SettingsContextKind): Boolean =
    kind >= widestKind && (narrowestKind == null || kind <= narrowestKind)

  override fun toString(): String = "$widestKind..${narrowestKind ?: "open"}"

  @Experimental
  companion object {
    /** Holds the range that covers every kind. A provider that declares no range has it. */
    val EVERY_KIND: SettingsContextKindRange = SettingsContextKindRange(SettingsContextKind.APPLICATION, narrowestKind = null)
  }
}

/** Tells the vault what [LayerProvider.writeData] did with the slices it was handed. */
@Experimental
enum class SaveResult {
  /** The store holds the slices. */
  SAVED,

  /** The provider did not store the slices, so the vault removes every layer of the store from its model. */
  DROPPED,
}
