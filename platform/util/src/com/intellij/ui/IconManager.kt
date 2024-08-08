// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.ui

import com.intellij.openapi.util.DummyIcon
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.RowIcon
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon

interface IconManager {
  companion object {
    private val isActivated = AtomicBoolean()
    @Volatile
    private var instance: IconManager? = null

    @JvmStatic
    fun getInstance(): IconManager = instance ?: DummyIconManager

    // icon Loader is quite heavy, better to not instantiate class unless required
    fun activate(manager: IconManager?) {
      if (!isActivated.compareAndSet(false, true)) {
        return
      }

      if (manager == null) {
        val implClass = IconManager::class.java.classLoader.loadClass("com.intellij.ui.icons.CoreIconManager")
        instance = MethodHandles.lookup().findConstructor(implClass, MethodType.methodType(Void.TYPE)).invoke() as IconManager
      }
      else {
        instance = manager
      }
    }

    @TestOnly
    fun deactivate() {
      if (isActivated.compareAndSet(true, false)) {
        instance = null
      }
    }
  }

  @Internal
  fun getPlatformIcon(id: PlatformIcons): Icon

  @Deprecated("Use getIcon(path, classLoader)")
  fun getIcon(path: String, aClass: Class<*>): Icon

  fun getIcon(path: String, classLoader: ClassLoader): Icon

  /**
   * Path must be specified without a leading slash, in a format for [ClassLoader.getResourceAsStream]
   */
  @Internal
  fun loadRasterizedIcon(path: String, classLoader: ClassLoader, cacheKey: Int, flags: Int): Icon

  @Internal
  fun loadRasterizedIcon(path: String, expUIPath: String?, classLoader: ClassLoader, cacheKey: Int, flags: Int): Icon

  fun createEmptyIcon(icon: Icon): Icon = icon

  fun createOffsetIcon(icon: Icon): Icon = icon

  fun createLayered(vararg icons: Icon): Icon

  fun colorize(g: Graphics2D, source: Icon, color: Color): Icon = source

  /**
   * @param param Unique key that WILL BE USED to cache the icon instance.
   * Prefer passing unique objects over [String] or [Integer] to avoid accidental clashes with another module.
   */
  fun <T : Any> createDeferredIcon(base: Icon?, param: T, iconProducer: (T) -> Icon?): Icon

  fun createLayeredIcon(instance: Iconable, icon: Icon, flags: Int): RowIcon

  fun createRowIcon(iconCount: Int): RowIcon = createRowIcon(iconCount = iconCount, alignment = RowIcon.Alignment.TOP)

  fun createRowIcon(iconCount: Int, alignment: RowIcon.Alignment): RowIcon

  fun createRowIcon(vararg icons: Icon): RowIcon

  fun registerIconLayer(flagMask: Int, icon: Icon)

  fun tooltipOnlyIfComposite(icon: Icon): Icon

  /**
   * @param icon the icon to which the colored badge should be added
   * @return an icon that paints the given icon with the colored badge
   */
  fun withIconBadge(icon: Icon, color: Paint): Icon = icon

  @ApiStatus.Experimental
  fun colorizedIcon(baseIcon: Icon, colorProvider: () -> Color): Icon = baseIcon

  @Internal
  fun hashClass(aClass: Class<*>): Long = aClass.hashCode().toLong()

  fun getPluginAndModuleId(classLoader: ClassLoader): Pair<String, String?> = "com.intellij" to null

  fun getClassLoader(pluginId: String, moduleId: String?): ClassLoader? = IconManager::class.java.classLoader

  @Internal
  fun getClassLoaderByClassName(className: String): ClassLoader? = IconManager::class.java.classLoader
}

private object DummyIconManager : IconManager {
  override fun getPlatformIcon(id: PlatformIcons): Icon = DummyIconImpl(id.testId ?: id.name)

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getIcon(path: String, aClass: Class<*>): Icon = DummyIconImpl(path)

  override fun getIcon(path: String, classLoader: ClassLoader): Icon = DummyIconImpl(path)

  override fun loadRasterizedIcon(path: String, classLoader: ClassLoader, cacheKey: Int, flags: Int): Icon = DummyIconImpl(path)

  override fun loadRasterizedIcon(path: String, expUIPath: String?, classLoader: ClassLoader, cacheKey: Int, flags: Int): Icon
    = DummyIconImpl(path, expUIPath)

  override fun createLayeredIcon(instance: Iconable, icon: Icon, flags: Int): RowIcon {
    val icons = arrayOfNulls<Icon>(2)
    icons[0] = icon
    return DummyRowIcon(icons)
  }

  override fun registerIconLayer(flagMask: Int, icon: Icon) {
  }

  override fun tooltipOnlyIfComposite(icon: Icon): Icon = icon

  override fun <T : Any> createDeferredIcon(base: Icon?, param: T, iconProducer: (T) -> Icon?): Icon = iconProducer(param) ?: base!!

  override fun createRowIcon(iconCount: Int, alignment: RowIcon.Alignment): RowIcon = DummyRowIcon(iconCount)

  override fun createLayered(vararg icons: Icon): Icon {
    @Suppress("UNCHECKED_CAST")
    return DummyRowIcon(icons as Array<Icon?>?)
  }

  override fun createRowIcon(vararg icons: Icon): RowIcon {
    @Suppress("UNCHECKED_CAST")
    return DummyRowIcon(icons as Array<Icon?>?)
  }
}

private class DummyRowIcon : DummyIconImpl, RowIcon {
  private var icons: Array<Icon?>?

  constructor(iconCount: Int) : super("<DummyRowIcon>") {
    icons = arrayOfNulls(iconCount)
  }

  constructor(icons: Array<Icon?>?) : super("<DummyRowIcon>") {
    this.icons = icons
  }

  override fun getIconCount(): Int = if (icons == null) 0 else icons!!.size

  override fun getIcon(index: Int): Icon? = icons!![index]

  override fun setIcon(icon: Icon, i: Int) {
    if (icons == null) {
      icons = arrayOfNulls(4)
    }
    icons!![i] = icon
  }

  override fun getDarkIcon(isDark: Boolean): Icon = this

  override fun getAllIcons(): List<Icon> {
    val list = ArrayList<Icon>()
    for (element in icons!!) {
      if (element != null) {
        list.add(element)
      }
    }
    return list
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    else {
      return other is DummyRowIcon && icons.contentEquals(other.icons)
    }
  }

  override fun hashCode(): Int = if (icons!!.isNotEmpty()) icons!![0].hashCode() else 0

  override fun toString(): String = "RowIcon(icons=${icons?.asList()})"

  override fun replaceBy(replacer: IconReplacer): Icon = this
}

private open class DummyIconImpl(override val originalPath: String, override val expUIPath: String? = null) : ScalableIcon, DummyIcon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
  }

  constructor(path: String) : this(path, null)

  override fun getIconWidth(): Int = 16

  override fun getIconHeight(): Int = 16

  override fun hashCode(): Int = originalPath.hashCode()

  override fun equals(other: Any?): Boolean {
    return this === other || other is DummyIconImpl && other.originalPath == originalPath
  }

  override fun toString(): String = originalPath

  override fun getScale(): Float = 1f

  override fun scale(scaleFactor: Float): Icon = this
}