// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("KDocUnresolvedReference")

package com.intellij

import com.intellij.BundleBase.partialMessage
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ArrayUtilRt
import com.intellij.util.DefaultBundleService
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.InputStreamReader
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.function.Supplier

/**
 * Base class for particular scoped bundles (e.g. `'vcs'` bundles, `'aop'` bundles etc.).
 * **This class is not supposed to be extended directly. Use [com.intellij.DynamicBundle] or [org.jetbrains.jps.api.JpsDynamicBundle] instead.**
 */
open class AbstractBundle {
  private var bundle: Reference<ResourceBundle>? = null
  private var defaultBundle: Reference<ResourceBundle>? = null

  @get:ApiStatus.Internal
  protected val bundleClassLoader: ClassLoader
  private val pathToBundle: @NonNls String

  /**
   * @param bundleClass a class to get the classloader, usually a class which declared the field which references the bundle instance
   */
  constructor(bundleClass: Class<*>, pathToBundle: @NonNls String) {
    this.pathToBundle = pathToBundle
    bundleClassLoader = bundleClass.classLoader
  }

  @Obsolete
  protected constructor(pathToBundle: @NonNls String) {
    this.pathToBundle = pathToBundle
    bundleClassLoader = javaClass.classLoader
  }

  companion object {
    @ApiStatus.Internal
    fun getControl(): ResourceBundle.Control = IntelliJResourceControl

    @Contract("null, _, _, _ -> param3")
    @JvmStatic
    fun messageOrDefault(bundle: ResourceBundle?, key: @NonNls String, defaultValue: @Nls String?, vararg params: Any?): @Nls String? {
      return when {
        bundle == null -> defaultValue
        !bundle.containsKey(key) -> postprocessValue(bundle = bundle, value = defaultValue ?: useDefaultValue(bundle, key), params = params)
        else -> com.intellij.messageOrDefault(bundle = bundle, key = key, defaultValue = defaultValue, params = params)
      }
    }

    @JvmStatic
    fun message(bundle: ResourceBundle, key: @NonNls String, vararg params: Any?): @Nls String {
      return com.intellij.messageOrDefault(bundle = bundle, key = key, defaultValue = null, params = params)
    }

    @Suppress("HardCodedStringLiteral")
    @JvmStatic
    fun messageOrNull(bundle: ResourceBundle, key: @NonNls String, vararg params: Any): @Nls String? {
      val value = messageOrDefault(bundle = bundle, key = key, defaultValue = key, params = params)
      return if (key == value) null else value
    }

    @ApiStatus.Internal
    fun resolveResourceBundleWithFallback(
      loader: ClassLoader,
      pathToBundle: String,
      firstTry: () -> ResourceBundle,
    ): ResourceBundle {
      try {
        return firstTry()
      }
      catch (_: MissingResourceException) {
        logger<AbstractBundle>().info("Cannot load resource bundle from *.properties file, falling back to slow class loading: $pathToBundle")
        ResourceBundle.clearCache(loader)
        return ResourceBundle.getBundle(pathToBundle, Locale.getDefault(), loader)
      }
    }
  }

  @Contract(pure = true)
  // open only to preserve compatibility
  open fun getMessage(key: @NonNls String, vararg params: Any?): @Nls String {
    return com.intellij.messageOrDefault(bundle = getResourceBundle(bundleClassLoader), key = key, defaultValue = null, params = params)
  }

  /**
   * Performs partial application of the pattern message from the bundle leaving some parameters unassigned.
   * It's expected that the message contains `params.length + unassignedParams` placeholders. Parameters
   * `{0}..{params.length-1}` will be substituted using a passed params array. The remaining parameters
   * will be renumbered: `{params.length}` will become `{0}` and so on, so the resulting template
   * could be applied once more.
   *
   * @param key resource key
   * @param unassignedParams number of unassigned parameters
   * @param params assigned parameters
   * @return a template suitable to pass to [MessageFormat.format] having the specified number of placeholders left
   */
  @Contract(pure = true)
  fun getPartialMessage(key: @NonNls String, unassignedParams: Int, vararg params: Any?): @Nls String {
    @Suppress("UNCHECKED_CAST")
    return partialMessage(bundle = getResourceBundle(bundleClassLoader), key = key, unassignedParams = unassignedParams, params = params as Array<Any?>)
  }

  // open only to preserve compatibility
  open fun getLazyMessage(key: @NonNls String, vararg params: Any?): Supplier<String> {
    // do not capture new empty Object[] arrays here
    val actualParams = if (params.isEmpty()) ArrayUtilRt.EMPTY_OBJECT_ARRAY else params
    return Supplier {
      getMessage(key = key, params = actualParams)
    }
  }

  open fun messageOrNull(key: @NonNls String, vararg params: Any): @Nls String? {
    return messageOrNull(bundle = getResourceBundle(), key = key, params = params)
  }

  // open only to preserve compatibility
  open fun messageOrDefault(key: @NonNls String, defaultValue: @Nls String?, vararg params: Any?): @Nls String? {
    return messageOrDefault(bundle = getResourceBundle(), key = key, defaultValue = defaultValue, params = params)
  }

  fun containsKey(key: @NonNls String): Boolean = getResourceBundle().containsKey(key)

  @ApiStatus.Internal
  fun getResourceBundle(): ResourceBundle = getResourceBundle(bundleClassLoader)

  @ApiStatus.Internal
  fun getResourceBundle(classLoader: ClassLoader): ResourceBundle {
    val isDefault = DefaultBundleService.isDefaultBundle()
    var bundle = getBundle(isDefault, classLoader)
    if (bundle == null) {
      bundle = resolveResourceBundleWithFallback(loader = classLoader, pathToBundle = pathToBundle) {
        findBundle(pathToBundle = pathToBundle, loader = classLoader, control = IntelliJResourceControl)
      }
      val ref = SoftReference(bundle)
      if (isDefault) {
        defaultBundle = ref
      }
      else {
        this.bundle = ref
      }
    }
    return bundle
  }

  @ApiStatus.Internal
  protected open fun getBundle(isDefault: Boolean, classLoader: ClassLoader): ResourceBundle? = (if (isDefault) defaultBundle else bundle)?.get()

  protected open fun findBundle(pathToBundle: @NonNls String, loader: ClassLoader, control: ResourceBundle.Control): ResourceBundle {
    return ResourceBundle.getBundle(pathToBundle, Locale.getDefault(), loader, control)
  }

  @ApiStatus.Internal
  protected fun findBundle(
    pathToBundle: @NonNls String,
    loader: ClassLoader,
    control: ResourceBundle.Control,
    locale: Locale,
  ): ResourceBundle {
    return ResourceBundle.getBundle(pathToBundle, locale, loader, control)
  }

  @Deprecated(
    """This method is no longer required.
    The Locale cache now gets cleared automatically after the initialization of the language plugin.""")
  fun clearLocaleCache() {
    bundle?.clear()
  }
}

@Suppress("FunctionName")
@ApiStatus.Internal
fun _doResolveBundle(loader: ClassLoader, locale: @NonNls Locale, pathToBundle: @NonNls String): ResourceBundle {
  return ResourceBundle.getBundle(pathToBundle, locale, loader, IntelliJResourceControl)
}

// UTF-8 control for Java <= 1.8.
// Before java9 ISO-8859-1 was used, in java 9 and above UTF-8.
// See https://docs.oracle.com/javase/9/docs/api/java/util/PropertyResourceBundle.html and
// https://docs.oracle.com/javase/8/docs/api/java/util/PropertyResourceBundle.html for more details
// For all Java version - use getResourceAsStream instead of "getResource -> openConnection" for performance reasons
private object IntelliJResourceControl : ResourceBundle.Control() {
  override fun getFormats(baseName: String): List<String> = FORMAT_PROPERTIES

  override fun newBundle(baseName: String, locale: Locale, format: String, loader: ClassLoader, reload: Boolean): ResourceBundle? {
    val bundleName = toBundleName(baseName, locale)
    // application protocol check
    val resourceName = (if (bundleName.contains("://")) null else toResourceName(bundleName, "properties")) ?: return null
    val stream = loader.getResourceAsStream(resourceName) ?: return null
    return stream.use {
      IntelliJResourceBundle(reader = InputStreamReader(it, StandardCharsets.UTF_8))
    }
  }
}