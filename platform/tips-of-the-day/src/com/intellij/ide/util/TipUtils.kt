// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment", "ReplacePutWithAssignment")

package com.intellij.ide.util

import com.intellij.CommonBundle
import com.intellij.featureStatistics.FeatureDescriptor
import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.text.paragraph.TextParagraph
import com.intellij.ide.util.TipUiSettings.imageBorderColor
import com.intellij.ide.util.TipUiSettings.imageMaxWidth
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
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

internal object TipUtils {
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

private data class LoadedTipInfo(val tipContent: @Nls String,
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
      @Suppress("HardCodedStringLiteral") val content = Files.readString(tipFile)
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
private const val tipDirectory = "tips"


private fun getTipRetrievers(tip: TipAndTrickBean): TipRetrieversInfo {
  // descriptor can be null if the provided tip is not registered as an extension
  // such tips are not present in the tips of the day list, but shown in the productivity guide
  val defaultLoader = tip.pluginDescriptor?.pluginClassLoader ?: TipUtils::class.java.classLoader

  val retrievers: MutableList<TipRetriever> = ArrayList()
  val locale = LocalizationUtil.getLocaleOrNullForDefault()
  if (locale != null) {
    //tips from locally placed localization
    retrievers.addAll(getLocalizationTipRetrievers(defaultLoader))
    val localizationPluginLoader: ClassLoader? = LocalizationUtil.getPluginClassLoader()
    if (localizationPluginLoader != null) {
      var ideCode = ApplicationInfoEx.getInstanceEx().apiVersionAsNumber.productCode.lowercase()
      //Let's just use the same set of tips here to save space. IC won't try displaying tips it is not aware of, so there'll be no trouble.
      if (ideCode.contains("ic")) ideCode = "iu"
      //So the primary loader is determined. Now we're constructing retrievers that use a pair of path/loaders to try to get the tips.
      val fallbackIdeCode = productCodeTipMap.getOrDefault(ideCode, ideCode)
      //tips from language plugin
      listOf(ideCode, fallbackIdeCode, "db_pl", "bdt", "misc").forEach { retrievers.add(TipRetriever(localizationPluginLoader, tipDirectory, it)) }
    }
  }
  val defaultRetriever = TipRetriever(defaultLoader, tipDirectory, "")
  retrievers.add(defaultRetriever)
  return TipRetrieversInfo(defaultRetriever, retrievers)
}

private fun getLocalizationTipRetrievers(loader: ClassLoader): List<TipRetriever> {
  val result = mutableListOf<TipRetriever>()
  val folderPaths = LocalizationUtil.getFolderLocalizedPaths(tipDirectory)
  val suffixes = LocalizationUtil.getLocalizationSuffixes()
  //folder paths and suffix paths should have the same size, because their elements depend on locale (if it contains a region or not)
  for (i in folderPaths.indices) {
    folderPaths.getOrNull(i)?.let {result.add(TipRetriever(loader, it, ""))}
    suffixes.getOrNull(i)?.let {result.add(TipRetriever(loader, tipDirectory, "", it))}
  }
  return result
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

private fun getCantReadText(bean: TipAndTrickBean): @Nls String {
  val plugin = getPoweredByText(bean)
  val product: @Nls String = if (!plugin.isEmpty()) {
    IdeBundle.message("product.and.plugin", ApplicationNamesInfo.getInstance().fullProductName, plugin)
  } else {
    ApplicationNamesInfo.getInstance().fullProductName
  }
  return IdeBundle.message("error.unable.to.read.tip.of.the.day", bean.fileName, product)
}

private fun getPoweredByText(tip: TipAndTrickBean): @NlsSafe String {
  val descriptor = tip.pluginDescriptor
  return if (descriptor == null || PluginManagerCore.CORE_ID == descriptor.pluginId) "" else descriptor.name
}

private class TipEntity(private val name: @NlsSafe String, private val value: @NlsSafe String) {
  fun inline(where: @Nls String): @Nls String {
    return where.replace("&$name;", value)
  }
}

private class TipRetriever(@JvmField val loader: ClassLoader?, @JvmField val path: String, @JvmField val subPath: String, val suffix: String = "") {
  fun getTipContent(tipName: String?): @Nls String? {
    if (tipName == null) {
      return null
    }

    val tipUrl = getTipUrl(tipName) ?: return null
    try {
      @Suppress("HardCodedStringLiteral")
      return ResourceUtil.loadText(tipUrl.openStream())
    }
    catch (ignored: IOException) {
      return null
    }
  }

  fun getTipUrl(tipName: String): URL? {
    val tipLocation = "/$path/${if (subPath.isNotEmpty()) "$subPath/" else ""}"
    val fileName = addSuffixToFileName(tipName, suffix)
    val tipUrl = ResourceUtil.getResource(loader!!, tipLocation, fileName)
    // tip is not found, but if its name starts with prefix, try without as a safety measure
    @Suppress("SpellCheckingInspection")
    if (tipUrl == null && tipName.startsWith("neue-")) {
      return ResourceUtil.getResource(loader, tipLocation, fileName.substring(5))
    }
    return tipUrl
  }

  private fun addSuffixToFileName(filename: String, suffix: String): String {
    if (suffix.isEmpty()) return filename
    val nameWithoutExtension = FileUtilRt.getNameWithoutExtension(filename)
    val extension = FileUtilRt.getExtension(filename)
    return "$nameWithoutExtension$suffix.$extension"
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
