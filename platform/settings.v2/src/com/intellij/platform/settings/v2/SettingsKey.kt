// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/change-listeners.spec.md
// @spec ../../../../../../spec/layer-resolution.spec.md
// @spec ../../../../../../spec/multidimensional-settings.spec.md
// @spec ../../../../../../spec/setting-keys.spec.md
// @spec ../../../../../../spec/setting-values.spec.md

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.TestOnly
import java.lang.invoke.MethodHandles
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * A typed, addressable identifier for a single setting. Code reads and writes settings through keys,
 * never through strings.
 *
 * A key carries the setting's [name], its [default] value, the [Serializer] that converts the value to
 * and from storage, and the [pluginId] of the owning plugin. Keys are declared as constants:
 *
 * ```
 * // declaration
 * val INDENT_SIZE = SettingsKey<Int>("editor.indent.size", default = 4)
 *
 * // read (synchronous)
 * val size = INDENT_SIZE[context]       // or INDENT_SIZE.getValue() / .getValue(project) / .getValue(psiFile)
 *
 * // write (suspending)
 * INDENT_SIZE[context] = 8              // or INDENT_SIZE.setValue(8) / .setValue(8, project)
 * ```
 *
 * **Identity.** Two keys are equal when they have the same [name] and the same owning [pluginId]. The second
 * construction of a key with the same owner and name is a runtime error. This catches typos and copy-paste bugs
 * early. A plugin that was unloaded and loaded again may declare its keys again. In storage a key is known by its
 * [identity], `prefix.name`. See [storagePrefix].
 *
 * **Default.** Every key carries a default. When no layer in the resolution chain supplies a value, a read returns
 * the default. So for a consumer a key is never "unset". For a heavy or runtime-dependent value, pass a
 * [defaultFactory] instead of an eager [default]. A factory of a slotted key learns the slot path of the read with
 * [accessFor].
 *
 * **Value type.** Every key has a [serializer]. For the built-in scalars [Int], [Long], [Double], [Boolean], and
 * [String], it is inferred from the default and may be omitted. Any other type must pass one explicitly. This
 * includes a [readonly] key, whose value is never persisted.
 *
 * Only the platform extends this class. [SettingsMapKey] is its only subclass.
 *
 * @see SettingsMapKey for slotted/multidimensional storage.
 */
@Experimental
@NonExtendable
open class SettingsKey<T>(
  /** The setting's name, unique within the owning plugin. In storage it follows the [storagePrefix]. See [identity]. */
  val name: String,
  /** Converts the value to and from storage. Always present, even for keys that are never persisted, for consistency. */
  val serializer: Serializer<T>,
  /**
   * When `true`, no context can write the key, and a read returns the default with no layer consulted. This is
   * typical for a runtime-derived value, such as the OS, the process id, or the remote-development side.
   */
  val readonly: Boolean = false,
  /**
   * The map key whose slotted storage holds the value of this key, or `null` for a top-level key. When set, the key
   * resolves through the slot of that storage that its discriminator selects. See [SettingsMapKey] for how slotted,
   * multidimensional storage works.
   */
  val storageKey: SettingsMapKey? = null,
  /**
   * Restricts which layers may hold this key. A read and a write consider only the providers that it accepts. The
   * default accepts every layer. It confines a key to some storage in every context, such as a key that must live
   * only in the layer of its plugin. A provider filters the keys in turn with [LayerProvider.acceptsKey], and a layer
   * counts only when both filters agree (ADR 0129).
   */
  val layerFilter: (LayerProvider) -> Boolean = { true },
  /**
   * Restricts the contexts where this key resolves. The chain skips a node that it rejects. On a read, such a node
   * holds no value. On a write, the walk up the chain passes over it, so the write goes on to the next accepted
   * context. The default accepts every context. It constrains the granularity of a key, such as an application-only
   * setting that a project or a file cannot override. Prefer a test of [SettingsContext.kind], which is the same for
   * an overlay and its base.
   */
  val contextFilter: (SettingsContext) -> Boolean = { true },
  /**
   * Validates a candidate value. It returns `null` when the value is acceptable, or a human-readable reason when it
   * is not. It is pure and has no side effects, so a UI can call it live while the user edits (see [validate]). The
   * write path calls it too and rejects an invalid write with [SettingsValidationException]. A validator that throws
   * rejects the value. The default accepts everything. The [default] of the key counts as valid and is not checked.
   */
  val validator: (T) -> String? = { null },
  /**
   * Tells which layer localities this key may live in: `SHAREABLE`, team or local, by default, or `ENVIRONMENTAL`
   * for a machine-bound value. A read and a write consider only the layers whose [LayerLocality] it accepts. A plain
   * write lands in [KeyLocality.defaultLayer]. See [KeyLocality].
   */
  val locality: KeyLocality = KeyLocality.SHAREABLE,
  /**
   * Produces the default value of the key for a context. A read calls it whenever no layer supplies a value. A
   * factory of a slotted key learns the slot path of the read with [accessFor]. See [default].
   */
  private val defaultFactory: (SettingsContext) -> T,
) {
  /**
   * The class loader of the class that declares this key, or `null` for the bootstrap loader.
   *
   * A plugin loads each of its modules with a loader of its own, so the loader tells the module of the key, and
   * whether that module is still loaded.
   */
  private val declaringClassLoader: ClassLoader? = declaringClass()?.classLoader

  /**
   * `true` when the module that declares this key unloads or is gone. A module never loads again with the same
   * loader, so once `true`, it stays `true`. It is `false` for a platform key.
   */
  @get:Internal
  val isOriginUnloading: Boolean
    get() = (declaringClassLoader as? PluginAwareClassLoader)?.state == PluginAwareClassLoader.UNLOAD_IN_PROGRESS

  /** Holds the owner that a test set with [withTestPluginId], or `null` when no test set one. */
  private var testPluginId: PluginId? = null

  /**
   * The owning plugin, or `null` for a platform key.
   *
   * Taken from [declaringClassLoader], which knows its plugin. So a key belongs to whoever wrote it, and no plugin
   * can declare a key of another plugin. See [withTestPluginId] for what a test does.
   */
  val pluginId: PluginId?
    get() = testPluginId ?: (declaringClassLoader as? PluginAwareClassLoader)?.pluginId

  private val storagePrefixResolution = lazy {
    val resolved = SettingsKeyPrefixBean.declaredPrefix(pluginId) ?: pluginId?.idString
    require(resolved == null || !resolved.startsWith(STORAGE_IDENTITY_PREFIX)) {
      "Settings key prefix '$resolved' of '$name' must not start with the reserved '$STORAGE_IDENTITY_PREFIX' marker " +
      "(it flags slotted storage)."
    }
    resolved
  }

  /**
   * The prefix this key's [identity] carries: the one its plugin declared (see [SettingsKeyPrefixBean]), else
   * the plugin id. `null` for a platform-core key that declares neither.
   *
   * Resolved on first use, not at construction: a declaration comes from an extension point, and a key is often
   * declared in a static initializer, which can run before the application.
   */
  @get:Internal
  val storagePrefix: String? by storagePrefixResolution

  /**
   * The storage identity of the key: `prefix.name`, or only [name] when the key has no prefix. Layer data is keyed
   * by this string, not by the [SettingsKey] object, so a value survives while the owning key is not loaded. See
   * [storagePrefix] for where the prefix comes from.
   */
  open val identity: String by lazy { rawIdentity(storagePrefix, name) }

  init {
    require(!name.startsWith(STORAGE_IDENTITY_PREFIX)) {
      "Settings key name '$name' must not start with the reserved '$STORAGE_IDENTITY_PREFIX' marker (it flags slotted storage)."
    }
    registerForCollisionCheck(pluginId, name, declaringClassLoader)
  }

  /**
   * Makes [pluginId] the owner of this key, as if a class of that plugin declared it, and returns the key.
   *
   * A test needs this because its own classes belong to no plugin, so a key it builds belongs to the platform.
   * Production code needs nothing: a key takes the plugin of the class that declares it.
   *
   * ```
   * val key = SettingsKey("name", default = "d").withTestPluginId(OWNER)
   * ```
   *
   * @throws IllegalStateException outside a unit test, or when the key has already resolved its [identity]
   */
  @TestOnly
  open fun withTestPluginId(pluginId: PluginId): SettingsKey<T> {
    check(ApplicationManager.getApplication()?.isUnitTestMode != false) { "withTestPluginId is for a unit test only." }
    check(!storagePrefixResolution.isInitialized()) {
      "Settings key '$name' has already resolved its identity. Set the test plugin id before the key is used."
    }
    registerForCollisionCheck(pluginId, name, declaringClassLoader)
    unregisterForCollisionCheck(this.pluginId, name)
    testPluginId = pluginId
    return this
  }

  /** Creates a key with an eager [default]. When [serializer] is `null`, it infers a built-in one from a scalar [default]. */
  constructor(
    name: String,
    default: T,
    serializer: Serializer<T>? = null,
    readonly: Boolean = false,
    storageKey: SettingsMapKey? = null,
    layerFilter: (LayerProvider) -> Boolean = { true },
    contextFilter: (SettingsContext) -> Boolean = { true },
    validator: (T) -> String? = { null },
    locality: KeyLocality = KeyLocality.SHAREABLE,
  ) : this(name, serializer ?: inferScalarSerializer(default), readonly, storageKey, layerFilter, contextFilter,
           validator, locality, { default })

  /** Computes the key's default value for [context]. Called by the platform whenever the default is needed. */
  fun default(context: SettingsContext): T = defaultFactory(context)

  /**
   * Resolves the value of this key against [context], as `key[context]`, and falls back to the default. A
   * [readonly] key returns its default at once. A child key, which has a [storageKey], resolves through the nested
   * storage of that map key. Any other key resolves through the top-level storage.
   */
  operator fun get(context: SettingsContext): T =
    if (readonly) default(context) else resolve(context)

  /**
   * Returns the value that the narrowest layer holding this key stores in [context]. Returns the default when no
   * layer holds the key or its stored value is malformed. A stored null is a value, so it does not fall back to the
   * default. [SettingsMapKey] overrides this to fail, because a map key is never read directly.
   */
  protected open fun resolve(context: SettingsContext): T {
    val stored = resolveRaw(context) ?: return default(context)
    return deserializeOrElse(stored) { default(context) }
  }

  /**
   * Deserializes a raw stored [value] through the [serializer], or returns the result of [onMalformed] if the value
   * cannot be read as this key's type.
   *
   * The config files are hand-editable, so a value can have the wrong type or shape for its key (a string where a
   * number is expected, a list where a scalar is, …); such a value must not break resolution, and its well-typed
   * siblings are unaffected. Control-flow exceptions propagate.
   */
  private inline fun deserializeOrElse(value: SettingsValue, onMalformed: () -> T): T =
    try {
      serializer.deserialize(value)
    }
    catch (e: ClassCastException) {
      LOG.warn("Ignoring malformed stored value for settings key '$identity'.", e)
      onMalformed()
    }

  /**
   * Returns the raw stored [SettingsValue] that this key resolves to against [context], **before** deserialization.
   * So the vault tracks a change by a compare of the stored values, with no deserialization on every check. It
   * ignores [readonly].
   *
   * @return the value of the narrowest layer that holds the key, or `null` when no layer holds one
   */
  @Internal
  fun resolveRaw(context: SettingsContext): SettingsValue? = storage(context).firstNotNullOfOrNull { it[this] }

  /**
   * Returns every deserialized stored value of this key along the resolution chain of [context], **widest layer
   * first**: application, project, and so on to the file. This is the reverse of the order that [get] scans, and it
   * is the natural fold order. `values(context).fold(seed) { acc, v -> merge(acc, v) }` combines the layers, and the
   * narrower one wins on a collision. A layer that does not hold the key, or holds a malformed value, is skipped. A
   * stored null is a value, so it is part of the result.
   *
   * The [default] of the key is **never** part of the result. A key with no stored values gives an **empty list**,
   * and so does every [readonly] key, which consults no layer. The caller decides how the default takes part, for
   * example as the fold seed: `values(context).fold(default(context)) { acc, v -> merge(acc, v) }`.
   *
   * Use it to combine the values instead of taking the narrowest one. For example, a union of the maps of a
   * map-valued key keeps the entries of a wider layer that a narrower layer does not hold.
   */
  fun values(context: SettingsContext): List<T> {
    if (readonly) return emptyList()
    return buildList {
      layers@ for (layer in storage(context).asReversed()) {
        val stored = layer[this@SettingsKey] ?: continue@layers
        add(deserializeOrElse(stored) { continue@layers })
      }
    }
  }

  /**
   * Writes [value] at [context] **or the nearest parent** that takes it, as `key[context] = value`. The write walks
   * up the context chain from [context] and skips each context that the [contextFilter] rejects. At each context it
   * tries the layers that the [layerFilter] and their [key filter][LayerProvider.acceptsKey] accept. It stops at the
   * first one that [offers a locality][LayerProvider.availableLocalities] for the key.
   *
   * A key with a [storageKey] needs [context] to pin a slot for every map key up its chain. See [accessFor]. A write
   * at a context of a project that is not open is dropped with a warning (ADR 0155).
   *
   * @throws IllegalArgumentException when the key is a [SettingsMapKey], which holds slots and is never written
   *   directly
   * @throws ReadOnlySettingsException when the key is [readonly]
   * @throws SettingsValidationException when [value] fails the [validator]
   * @throws UnpinnedDiscriminatorException when the key is slotted and [context] pins no slot for its storage chain
   * @throws IllegalStateException when no layer in the chain takes the write, or another key holds the [identity]
   */
  suspend operator fun set(context: SettingsContext, value: T) {
    SettingsVault.getInstance().set(context, this, value)
  }

  /**
   * Checks [value] against this key's [validator]. It never throws, so a UI can call it live on user input before a
   * write. A write performs the same check and throws [SettingsValidationException] on failure (ADR 0042). A
   * validator that throws counts as a rejection, and the engine logs the error.
   *
   * @return `null` when [value] is acceptable, or the reason it is not
   */
  fun validate(value: T): String? =
    try {
      validator(value)
    }
    catch (e: RuntimeException) {
      rethrowControlFlowException(e)
      LOG.error("Validator of settings key '$identity' failed", e)
      VALIDATOR_FAILED_REASON
    }

  /**
   * Registers a key-only [listener] that fires whenever the value of this key **may have changed**. It gets the key
   * and the context where the change was detected, but no value, so the owner reads again what it needs. It fires on
   * a change of this key and, for a slotted key, of a discriminator that the key depends on. The registration ends
   * when [disposable] is disposed.
   *
   * @throws IllegalArgumentException when this key is a [SettingsMapKey]. Listen on its child keys instead.
   */
  fun addListener(disposable: Disposable, listener: SettingsChangeListener): Unit =
    SettingsVault.getInstance().addListener(this, disposable, listener)

  /**
   * Registers a [listener] that fires whenever the **stored value** that this key resolves in [context] changes. It
   * fires only when that stored value differs, not on every unrelated write. The stored value is not the value that
   * a read returns: a write of the default fires it when no layer held a value before (ADR 0044). It gets the key and
   * [context] but no value. Read `this[context]` in the callback for the current one. The registration ends when
   * [disposable] is disposed.
   *
   * @throws IllegalArgumentException when this key is a [SettingsMapKey]. Listen on its child keys instead.
   */
  fun addListener(disposable: Disposable, context: SettingsContext, listener: SettingsChangeListener): Unit =
    SettingsVault.getInstance().addListener(this, context, disposable, listener)

  /**
   * Returns the read-only storage that this key resolves against. For a child key, it is the nested storage of the
   * [storageKey]. For any other key, it is the context snapshot.
   *
   * @param filterKey the leaf key of the read, whose filters apply. It is this key for a direct read. It is the child
   *   key when a map key resolves its nested storage, so the filters of the child key apply.
   */
  protected fun storage(context: SettingsContext, filterKey: SettingsKey<*> = this): List<SettingsValue.MapValue> =
    storageKey?.subStorage(context, filterKey) ?: SettingsVault.getInstance().snapshot(context, filterKey)

  /**
   * Checks that [context] pins a slot for every [SettingsMapKey] up this key's [storageChain].
   *
   * @throws UnpinnedDiscriminatorException for the innermost map key with no pinned slot.
   */
  @Internal
  fun requirePinnedSlots(context: SettingsContext): Unit =
    storageChain().forEach { it.pinnedDiscriminatorValue(context, this) }

  /** Returns this key's storage chain, innermost ([storageKey]) first. The sequence is empty for a plain key. */
  @Internal
  fun storageChain(): Sequence<SettingsMapKey> = generateSequence(storageKey) { it.storageKey }

  final override fun equals(other: Any?): Boolean =
    other is SettingsKey<*> && other.name == name && other.pluginId == pluginId

  final override fun hashCode(): Int = 31 * name.hashCode() + pluginId.hashCode()

  override fun toString(): String = "SettingsKey(name=$name, pluginId=$pluginId)"

  companion object {
    private val LOG = logger<SettingsKey<*>>()

    private const val VALIDATOR_FAILED_REASON: String = "the validator failed"

    /** Prefixes the storage [identity] of a [SettingsMapKey] so a layer node can be told apart from a plain map/object value. */
    @Internal
    const val STORAGE_IDENTITY_PREFIX: String = "@"

    /** Returns the bare storage identity of a [prefix] and [name] pair, without any marker prefix. */
    internal fun rawIdentity(prefix: String?, name: String): String =
      if (prefix == null) name else "$prefix.$name"

    /**
     * Holds the class loader that declared each owner and name pair. The reference is weak, so an unloaded plugin is
     * not kept alive.
     */
    private val registered = ConcurrentHashMap<Pair<PluginId?, String>, WeakReference<ClassLoader?>>()

    /**
     * Records that [classLoader] declares the key [name] of [pluginId].
     *
     * @throws IllegalArgumentException when a live class loader already declares a key with the same owner and name
     */
    private fun registerForCollisionCheck(pluginId: PluginId?, name: String, classLoader: ClassLoader?) {
      registered.compute(pluginId to name) { _, declared ->
        require(declared == null || isReload(declared.get(), classLoader)) {
          "A settings key named '$name' is already registered for plugin '$pluginId'. " +
          "Key names must be unique within an owning plugin."
        }
        WeakReference(classLoader)
      }
    }

    /**
     * Returns whether [current] declares a key again after its plugin was unloaded from [previous].
     *
     * The state of [previous] is checked, not only its weak reference. A reload can come before the garbage collection
     * clears the reference (ADR 0112).
     */
    private fun isReload(previous: ClassLoader?, current: ClassLoader?): Boolean =
      previous !== current &&
      (previous == null || previous is PluginAwareClassLoader && previous.state != PluginAwareClassLoader.ACTIVE)

    private fun unregisterForCollisionCheck(pluginId: PluginId?, name: String) {
      registered.remove(pluginId to name)
    }
  }
}

/**
 * Returns the class that declares the key under construction.
 *
 * The declaring class is the first frame that does not build a key. See [isKeyConstruction] for the frames that do.
 * A platform class in this package can declare a key too, so the package tells nothing.
 */
private fun declaringClass(): Class<*>? =
  StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk { frames ->
    frames.filter { !isKeyConstruction(it) }.map { it.declaringClass }.findFirst().orElse(null)
  }

/**
 * Returns whether [frame] builds a key.
 *
 * These are the constructors of [SettingsKey] and its subclasses, and the functions of this file. Other code of a
 * subclass, for example its static initializer, can declare a key.
 */
private fun isKeyConstruction(frame: StackWalker.StackFrame): Boolean =
  frame.declaringClass == KEY_FACTORY_FILE_CLASS ||
  frame.methodName == CONSTRUCTOR_METHOD_NAME && SettingsKey::class.java.isAssignableFrom(frame.declaringClass)

private const val CONSTRUCTOR_METHOD_NAME = "<init>"

/** The JVM class of this file. It holds the key factory functions and [declaringClass]. */
private val KEY_FACTORY_FILE_CLASS: Class<*> = MethodHandles.lookup().lookupClass()

/**
 * Creates a key with no explicit default. Its value is `null` until a layer supplies one, and its
 * [SettingsKey.serializer] is inferred from [T] (see [inferSerializer]). [T] must be nullable, as in
 * `SettingsKey<String?>("x")`. For a non-null key, pass a `default` through the other constructor.
 *
 * @throws IllegalArgumentException when [T] is not nullable
 */
@Experimental
inline fun <reified T> SettingsKey(
  name: String,
  readonly: Boolean = false,
  storageKey: SettingsMapKey? = null,
  noinline layerFilter: (LayerProvider) -> Boolean = { true },
  noinline contextFilter: (SettingsContext) -> Boolean = { true },
  noinline validator: (T) -> String? = { null },
  locality: KeyLocality = KeyLocality.SHAREABLE,
): SettingsKey<T> {
  require(null is T) { "SettingsKey<T>(\"$name\") without a default needs a nullable T, e.g. SettingsKey<String?>(\"$name\")." }
  @Suppress("UNCHECKED_CAST")
  return SettingsKey(name, inferSerializer<T>(), readonly, storageKey, layerFilter, contextFilter, validator,
                     locality) { null as T }
}

/** Thrown on a write of a [readonly][SettingsKey.readonly] key. */
@Experimental
class ReadOnlySettingsException(val key: SettingsKey<*>) :
  IllegalStateException("Settings key '${key.name}' is readonly and cannot be written.")

/** Thrown when a write of the slotted [key] finds no pinned slot for the discriminator of [mapKey]. See [accessFor]. */
@Experimental
class UnpinnedDiscriminatorException(val key: SettingsKey<*>, val mapKey: SettingsMapKey) : IllegalArgumentException(
  "Writing '${key.name}' needs the '${mapKey.discriminatorKey.name}' discriminator of '${mapKey.name}' pinned on the context. " +
  "Use 'context.accessFor(key)' to pin the slots currently selected, or 'context.access { this[discriminator] = slot }' to name one."
)

/** Thrown on a write of a value that the [validator][SettingsKey.validator] of the key rejects. */
@Experimental
class SettingsValidationException(val key: SettingsKey<*>, val value: Any?, val reason: String) :
  IllegalArgumentException("Invalid value for settings key '${key.name}': $reason")
