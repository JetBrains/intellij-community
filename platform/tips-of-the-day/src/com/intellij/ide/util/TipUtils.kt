// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment", "ReplacePutWithAssignment")

package com.intellij.ide.util

import com.intellij.CommonBundle
import com.intellij.DynamicBundle
import com.intellij.featureStatistics.FeatureDescriptor
import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.text.paragraph.TextParagraph
import com.intellij.ide.util.TipUiSettings.imageBorderColor
import com.intellij.ide.util.TipUiSettings.imageMaxWidth
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.icons.loadImageByClassLoader
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil.scale
import com.intellij.util.ImageLoader.loadFromUrl
import com.intellij.util.ResourceUtil
import com.intellij.util.ui.JBImageIcon
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.text.StyleConstants

private val LOG = logger<TipUtils>()

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

private val ENTITIES: List<TipEntity> = ApplicationInfo.getInstance().let { appInfo ->
  java.util.List.of(
    TipEntity("productName", ApplicationNamesInfo.getInstance().fullProductName),
    TipEntity("majorVersion", appInfo.majorVersion),
    TipEntity("minorVersion", appInfo.minorVersion),
    TipEntity("majorMinorVersion", "${appInfo.majorVersion}${if ("0" == appInfo.minorVersion) "" else "." + appInfo.minorVersion}"),
    TipEntity("settingsPath", CommonBundle.settingsActionPath()))
}

object TipUtils {
  fun getTip(feature: FeatureDescriptor?): TipAndTrickBean? {
    if (feature == null) {
      return null
    }

    val tipId = feature.tipId
    if (tipId == null) {
      LOG.warn("No Tip of the day for feature ${feature.id}")
      return null
    }

    var tip = TipAndTrickBean.findById(tipId)
    if (tip == null && tipId.isNotEmpty()) {
      tip = TipAndTrickBean()
      tip.fileName = tipId + TipAndTrickBean.TIP_FILE_EXTENSION
    }
    return tip
  }

  @JvmStatic
  fun getGroupDisplayNameForTip(tip: TipAndTrickBean): @Nls String? {
    val registry = ProductivityFeaturesRegistry.getInstance() ?: return null
    val featureDescriptor = registry.featureIds.asSequence()
      .map { registry.getFeatureDescriptor(it) }
      .firstOrNull { it.tipId == tip.id }
    return registry.getGroupDescriptor(featureDescriptor?.groupId ?: return null)?.displayName
  }

  /**
   * @param contextComponent is used for obtaining a system scale to properly load and scale the images
   */
  @JvmStatic
  fun loadAndParseTip(tip: TipAndTrickBean, contextComponent: Component?): List<TextParagraph> {
    return loadAndParseTip(tip = tip, contextComponent = contextComponent, isStrict = false)
  }

  /**
   * Throws exception on any issue occurred during tip loading and parsing
   */
  @TestOnly
  fun loadAndParseTipStrict(tip: TipAndTrickBean): List<TextParagraph> {
    return loadAndParseTip(tip = tip, contextComponent = null, isStrict = true)
  }

  fun checkTipFileExist(tip: TipAndTrickBean): Boolean {
    val retrievers = getTipRetrievers(tip)
    for (retriever in retrievers.tipRetrievers) {
      if (retriever.getTipUrl(tip.fileName) != null) {
        return true
      }
    }
    return false
  }
}

private fun loadAndParseTip(tip: TipAndTrickBean?, contextComponent: Component?, isStrict: Boolean): List<TextParagraph> {
  val currentTip = loadTip(tip = tip, isStrict = isStrict)
  val tipHtml = Jsoup.parse(currentTip.tipContent)
  val tipContent = tipHtml.body()

  val icons = loadImages(tipContent,
                         currentTip.tipImagesLoader,
                         currentTip.tipContentLoader,
                         currentTip.imagesLocation,
                         contextComponent,
                         isStrict)
  inlineProductInfo(tipContent)
  val paragraphs = TipContentConverter(tipContent, icons, isStrict).convert()
  if (paragraphs.isEmpty()) {
    handleWarning("Parsed paragraphs is empty for tip: $tip", isStrict)
  }
  else {
    paragraphs[0].editAttributes {
      StyleConstants.setSpaceAbove(this, TextParagraph.NO_INDENT)
    }
  }
  return paragraphs
}

private data class LoadedTipInfo(val tipContent: String,
                                 val imagesLocation: String? = null,
                                 val tipContentLoader: ClassLoader? = null,
                                 val tipImagesLoader: ClassLoader? = tipContentLoader)

private fun loadTip(tip: TipAndTrickBean?, isStrict: Boolean): LoadedTipInfo {
  if (tip == null) {
    return LoadedTipInfo(IdeBundle.message("no.tip.of.the.day"))
  }

  try {
    val tipFile = Path.of(tip.fileName)
    if (tipFile.isAbsolute && Files.exists(tipFile)) {
      val content = Files.readString(tipFile)
      return LoadedTipInfo(content, tipFile.parent.toString())
    }
    else {
      val retrieversInfo = getTipRetrievers(tip)
      for (retriever in retrieversInfo.tipRetrievers) {
        val tipContent = retriever.getTipContent(tip.fileName)
        if (tipContent != null) {
          val imagesTipRetriever = retrieversInfo.imagesTipRetriever
          val tipImagesLocation = "${imagesTipRetriever.path}/${if (imagesTipRetriever.subPath.isNotEmpty()) "${imagesTipRetriever.subPath}/" else ""}"
          return LoadedTipInfo(tipContent, tipImagesLocation, retriever.loader, imagesTipRetriever.loader)
        }
      }
    }
  }
  catch (e: IOException) {
    handleError(e, isStrict)
  }
  //All retrievers have failed or error occurred, return error.
  return LoadedTipInfo(getCantReadText(tip))
}

//mainClassLoader will always point to the loader that was used to start IDE, and it will be able to retrieve
//images from english tips of the day resources
private data class TipRetrieversInfo(val imagesTipRetriever: TipRetriever,
                                     var tipRetrievers: List<TipRetriever>)

private val productCodeTipMap = mapOf(Pair("iu", "ij"),
                                      Pair("pc", "py_ce"),
                                      Pair("ds", "py_ds"))

private fun getTipRetrievers(tip: TipAndTrickBean): TipRetrieversInfo {
  val fallbackLoader = TipUtils::class.java.classLoader
  // descriptor can be null if the provided tip is not registered as an extension
  // such tips are not present in the tips of the day list, but shown in the productivity guide
  val pluginDescriptor: PluginDescriptor? = tip.pluginDescriptor
  val langBundle = DynamicBundle.findLanguageBundle()

  //I know of ternary operators, but in cases like this they're harder to comprehend and debug than this.
  var tipLoader: ClassLoader? = null
  if (langBundle != null) {
    val langBundleLoader = langBundle.pluginDescriptor
    if (langBundleLoader != null) tipLoader = langBundleLoader.pluginClassLoader
  }
  val pluginClassLoader = pluginDescriptor?.pluginClassLoader

  if (tipLoader == null && pluginClassLoader != null) {
    tipLoader = pluginClassLoader
  }
  if (tipLoader == null) tipLoader = fallbackLoader
  var ideCode = ApplicationInfoEx.getInstanceEx().apiVersionAsNumber.productCode.lowercase()
  //Let's just use the same set of tips here to save space. IC won't try displaying tips it is not aware of, so there'll be no trouble.
  if (ideCode.contains("ic")) ideCode = "iu"
  //So the primary loader is determined. Now we're constructing retrievers that use a pair of path/loaders to try to get the tips.
  val fallbackIdeCode = productCodeTipMap.getOrDefault(ideCode, ideCode)
  val retrievers: MutableList<TipRetriever> = ArrayList()

  listOf(ideCode, fallbackIdeCode, "db_pl", "bdt", "misc", "").forEach { retrievers.add(TipRetriever(tipLoader, "tips", it)) }
  val fallbackRetriever = TipRetriever(if (pluginClassLoader == null) fallbackLoader else pluginClassLoader, "tips", "")
  retrievers.add(fallbackRetriever)
  return TipRetrieversInfo(fallbackRetriever, retrievers)
}

//Because images are not localized, we're always loading them from main classloader, but some of the images might not be present there
//so we're providing a secondary loader as backup to try plugin resources (e.g. Kotlin) that might have brought these images along.
private fun loadImages(tipContent: Element,
                       primaryImagesLoader: ClassLoader?,
                       secondaryImagesLoader: ClassLoader?,
                       tipsPath: String?,
                       contextComponent: Component?,
                       isStrict: Boolean): Map<String, Icon> {
  if (tipsPath == null) {
    return emptyMap()
  }

  val icons = HashMap<String, Icon>()
  for (imgElement in tipContent.getElementsByTag("img")) {
    if (!imgElement.hasAttr("src")) {
      handleWarning("Not found src attribute in img element:\n$imgElement", isStrict)
      continue
    }

    val path = imgElement.attr("src")
    var image: Image? = null
    if (primaryImagesLoader == null) {
      // This case is required only for testing by opening tip from the file (see TipDialog.OpenTipsAction)
      try {
        val imageUrl = File(tipsPath, path).toURI().toURL()
        image = loadFromUrl(imageUrl)
      }
      catch (e: MalformedURLException) {
        handleError(e, isStrict)
      }
      // This case is required only for Startdust Tips of the Day preview
      if (image == null) {
        try {
          val imageUrl = URL(null, path)
          image = loadFromUrl(imageUrl)
        }
        catch (e: MalformedURLException) {
          handleError(e, isStrict)
        }
      }
    }
    else {
      image = loadImageByClassLoader(path = "$tipsPath$path",
                                     classLoader = primaryImagesLoader,
                                     scaleContext = ScaleContext.create(contextComponent))
      if (image == null && secondaryImagesLoader != null)
        image = loadImageByClassLoader(path = "$tipsPath$path",
                                       classLoader = secondaryImagesLoader,
                                       scaleContext = ScaleContext.create(contextComponent))
    }
    if (image != null) {
      var icon: Icon = JBImageIcon(image)
      val maxWidth = imageMaxWidth
      if (icon.iconWidth > maxWidth) {
        icon = scale(icon, null, maxWidth * 1f / icon.iconWidth)
      }
      icons.put(path, icon)
    }
    else {
      handleWarning("Not found icon for path: $tipsPath$path", isStrict)
    }
  }
  return icons
}

private fun inlineProductInfo(tipContent: Element) {
  // Inline all custom entities like productName
  for (element in tipContent.getElementsContainingOwnText("&")) {
    var text = element.text()
    for (entity in ENTITIES) {
      text = entity.inline(text)
    }
    element.text(text)
  }
}

private fun handleWarning(message: String, isStrict: Boolean) {
  if (isStrict) {
    throw RuntimeException("Warning: $message")
  }
  else {
    LOG.warn(message)
  }
}

private fun handleError(t: Throwable, isStrict: Boolean) {
  if (isStrict) {
    throw t
  }
  else {
    LOG.warn(t)
  }
}

private fun getCantReadText(bean: TipAndTrickBean): String {
  val plugin = getPoweredByText(bean)
  var product = ApplicationNamesInfo.getInstance().fullProductName
  if (!plugin.isEmpty()) {
    product += " and $plugin plugin"
  }
  return IdeBundle.message("error.unable.to.read.tip.of.the.day", bean.fileName, product)
}

private fun getPoweredByText(tip: TipAndTrickBean): @NlsSafe String {
  val descriptor = tip.pluginDescriptor
  return if (descriptor == null || PluginManagerCore.CORE_ID == descriptor.pluginId) "" else descriptor.name
}

private class TipEntity(private val name: String, private val value: String) {
  fun inline(where: String): String {
    return where.replace("&$name;", value)
  }
}

private class TipRetriever(@JvmField val loader: ClassLoader?, @JvmField val path: String, @JvmField val subPath: String) {
  fun getTipContent(tipName: String?): String? {
    if (tipName == null) {
      return null
    }

    val tipUrl = getTipUrl(tipName) ?: return null
    try {
      return ResourceUtil.loadText(tipUrl.openStream())
    }
    catch (ignored: IOException) {
      return null
    }
  }

  fun getTipUrl(tipName: String): URL? {
    val tipLocation = "/$path/${if (subPath.isNotEmpty()) "$subPath/" else ""}"
    val tipUrl = ResourceUtil.getResource(loader!!, tipLocation, tipName)
    // tip is not found, but if its name starts with prefix, try without as a safety measure
    @Suppress("SpellCheckingInspection")
    if (tipUrl == null && tipName.startsWith("neue-")) {
      return ResourceUtil.getResource(loader, tipLocation, tipName.substring(5))
    }
    return tipUrl
  }
}

internal class IconWithRoundedBorder(private val delegate: Icon) : Icon {
  override fun getIconWidth(): Int = delegate.iconWidth

  override fun getIconHeight(): Int = delegate.iconHeight

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    val g2d = g.create() as Graphics2D
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val arcSize = scale(16).toFloat()
    val clipBounds = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), iconWidth.toFloat(), iconHeight.toFloat(), arcSize, arcSize)
    g2d.clip(clipBounds)
    delegate.paintIcon(c, g2d, x, y)
    g2d.paint = imageBorderColor
    g2d.stroke = BasicStroke(2f)
    g2d.draw(clipBounds)
    g2d.dispose()
  }
}
