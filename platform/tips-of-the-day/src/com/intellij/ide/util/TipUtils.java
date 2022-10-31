// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.DynamicBundle;
import com.intellij.featureStatistics.FeatureDescriptor;
import com.intellij.featureStatistics.GroupDescriptor;
import com.intellij.featureStatistics.ProductivityFeaturesRegistry;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.text.paragraph.TextParagraph;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.icons.LoadIconParameters;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import com.intellij.util.ResourceUtil;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.StartupUiUtil;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.swing.*;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;

import static com.intellij.DynamicBundle.findLanguageBundle;
import static com.intellij.util.ImageLoader.*;

public final class TipUtils {
  private static final Logger LOG = Logger.getInstance(TipUtils.class);
  private static final List<TipEntity> ENTITIES;

  static {
    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    ENTITIES = List.of(
      TipEntity.of("productName", ApplicationNamesInfo.getInstance().getFullProductName()),
      TipEntity.of("majorVersion", appInfo.getMajorVersion()),
      TipEntity.of("minorVersion", appInfo.getMinorVersion()),
      TipEntity.of("majorMinorVersion", appInfo.getMajorVersion() +
                                        ("0".equals(appInfo.getMinorVersion()) ? "" :
                                         ("." + appInfo.getMinorVersion()))),
      TipEntity.of("settingsPath", CommonBundle.settingsActionPath()));
  }

  private TipUtils() {
  }

  public static @Nullable TipAndTrickBean getTip(@Nullable FeatureDescriptor feature) {
    if (feature == null) return null;
    String tipId = feature.getTipId();
    if (tipId == null) {
      LOG.warn("No Tip of the day for feature " + feature.getId());
      return null;
    }

    TipAndTrickBean tip = TipAndTrickBean.findById(tipId);
    if (tip == null && StringUtil.isNotEmpty(tipId)) {
      tip = new TipAndTrickBean();
      tip.fileName = tipId + TipAndTrickBean.TIP_FILE_EXTENSION;
    }
    return tip;
  }

  public static @Nullable @Nls String getGroupDisplayNameForTip(@NotNull TipAndTrickBean tip) {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    if (registry == null) return null;
    return registry.getFeatureIds().stream()
      .map(featureId -> registry.getFeatureDescriptor(featureId))
      .filter(descriptor -> Objects.equals(descriptor.getTipId(), tip.getId()))
      .findFirst()
      .map(feature -> {
        String groupId = feature.getGroupId();
        if (groupId == null) return null;
        GroupDescriptor group = registry.getGroupDescriptor(groupId);
        return group != null ? group.getDisplayName() : null;
      })
      .orElse(null);
  }

  public static List<TextParagraph> loadAndParseTip(@Nullable TipAndTrickBean tip) {
    return loadAndParseTip(tip, false);
  }

  /**
   * Throws exception on any issue occurred during tip loading and parsing
   */
  @TestOnly
  public static List<TextParagraph> loadAndParseTipStrict(@Nullable TipAndTrickBean tip) {
    return loadAndParseTip(tip, true);
  }

  private static List<TextParagraph> loadAndParseTip(@Nullable TipAndTrickBean tip, boolean isStrict) {
    Trinity<@NotNull String, @Nullable ClassLoader, @Nullable String> result = loadTip(tip, isStrict);
    String text = result.first;
    @Nullable ClassLoader loader = result.second;
    @Nullable String tipsPath = result.third;

    Document tipHtml = Jsoup.parse(text);
    Element tipContent = tipHtml.body();

    Map<String, Icon> icons = loadImages(tipContent, loader, tipsPath, isStrict);
    inlineProductInfo(tipContent);

    List<TextParagraph> paragraphs = new TipContentConverter(tipContent, icons, isStrict).convert();
    if (paragraphs.size() > 0) {
      paragraphs.get(0).editAttributes(attr -> {
        StyleConstants.setSpaceAbove(attr, TextParagraph.NO_INDENT);
        return Unit.INSTANCE;
      });
    }
    else {
      handleWarning("Parsed paragraphs is empty for tip: " + tip, isStrict);
    }
    return paragraphs;
  }

  private static Trinity<@NotNull String, @Nullable ClassLoader, @Nullable String> loadTip(@Nullable TipAndTrickBean tip,
                                                                                           boolean isStrict) {
    if (tip == null) return Trinity.create(IdeBundle.message("no.tip.of.the.day"), null, null);
    try {
      File tipFile = new File(tip.fileName);
      if (tipFile.isAbsolute() && tipFile.exists()) {
        String content = FileUtil.loadFile(tipFile, StandardCharsets.UTF_8);
        return Trinity.create(content, null, tipFile.getParentFile().getAbsolutePath());
      }
      else {
        List<TipRetriever> retrievers = getTipRetrievers(tip);
        for (final TipRetriever retriever : retrievers) {
          String tipContent = retriever.getTipContent(tip.fileName);
          if (tipContent != null) {
            final String tipImagesLocation =
              String.format("/%s/%s", retriever.myPath, retriever.mySubPath.length() > 0 ? retriever.mySubPath + "/" : "");
            return Trinity.create(tipContent, retriever.myLoader, tipImagesLocation);
          }
        }
      }
    }
    catch (IOException e) {
      handleError(e, isStrict);
    }
    //All retrievers have failed or error occurred, return error.
    return Trinity.create(getCantReadText(tip), null, null);
  }

  public static boolean checkTipFileExist(@NotNull TipAndTrickBean tip) {
    List<TipRetriever> retrievers = getTipRetrievers(tip);
    for (TipRetriever retriever : retrievers) {
      if (retriever.getTipUrl(tip.fileName) != null) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static List<TipRetriever> getTipRetrievers(@NotNull TipAndTrickBean tip) {
    final ClassLoader fallbackLoader = TipUtils.class.getClassLoader();
    final PluginDescriptor pluginDescriptor = tip.getPluginDescriptor();
    final DynamicBundle.LanguageBundleEP langBundle = findLanguageBundle();

    //I know of ternary operators, but in cases like this they're harder to comprehend and debug than this.
    ClassLoader tipLoader = null;

    if (langBundle != null) {
      final PluginDescriptor langBundleLoader = langBundle.pluginDescriptor;
      if (langBundleLoader != null) tipLoader = langBundleLoader.getPluginClassLoader();
    }

    if (tipLoader == null && pluginDescriptor != null && pluginDescriptor.getPluginClassLoader() != null) {
      tipLoader = pluginDescriptor.getPluginClassLoader();
    }

    if (tipLoader == null) tipLoader = fallbackLoader;

    String ideCode = ApplicationInfoEx.getInstanceEx().getApiVersionAsNumber().getProductCode().toLowerCase(Locale.ROOT);
    //Let's just use the same set of tips here to save space. IC won't try displaying tips it is not aware of, so there'll be no trouble.
    if (ideCode.contains("ic")) ideCode = "iu";
    //So primary loader is determined. Now we're constructing retrievers that use a pair of path/loader to try to get the tips.
    final List<TipRetriever> retrievers = new ArrayList<>();

    retrievers.add(new TipRetriever(tipLoader, "tips", ideCode));
    retrievers.add(new TipRetriever(tipLoader, "tips", "misc"));
    retrievers.add(new TipRetriever(tipLoader, "tips", ""));
    retrievers.add(new TipRetriever(fallbackLoader, "tips", ""));

    return retrievers;
  }

  private static Map<String, Icon> loadImages(@NotNull Element tipContent,
                                              @Nullable ClassLoader loader,
                                              @Nullable String tipsPath,
                                              boolean isStrict) {
    if (tipsPath == null) return Collections.emptyMap();
    Map<String, Icon> icons = new HashMap<>();
    tipContent.getElementsByTag("img").forEach(imgElement -> {
      if (!imgElement.hasAttr("src")) {
        handleWarning("Not found src attribute in img element:\n" + imgElement, isStrict);
        return;
      }
      String path = imgElement.attr("src");

      Image image = null;
      if (loader == null) {
        // This case is required only for testing by opening tip from the file (see TipDialog.OpenTipsAction)
        try {
          URL imageUrl = new File(tipsPath, path).toURI().toURL();
          image = loadFromUrl(imageUrl);
        }
        catch (MalformedURLException e) {
          handleError(e, isStrict);
        }
        // This case is required only for Startdust Tips of the Day preview
        if (image == null) {
          try {
            URL imageUrl = new URL(null, path);
            image = loadFromUrl(imageUrl);
          }
          catch (MalformedURLException e) {
            handleError(e, isStrict);
          }
        }
      }
      else {
        int flags = USE_SVG | ALLOW_FLOAT_SCALING | USE_CACHE;
        boolean isDark = StartupUiUtil.isUnderDarcula();
        if (isDark) {
          flags |= USE_DARK;
        }
        image = loadImage(tipsPath + path, LoadIconParameters.defaultParameters(isDark),
                          null, loader, flags, !path.endsWith(".svg"));
      }

      if (image != null) {
        Icon icon = new JBImageIcon(image);
        int maxWidth = TipUiSettings.getImageMaxWidth();
        if (icon.getIconWidth() > maxWidth) {
          icon = IconUtil.scale(icon, null, maxWidth * 1f / icon.getIconWidth());
        }
        icons.put(path, icon);
      }
      else {
        handleWarning("Not found icon for path: " + path, isStrict);
      }
    });
    return icons;
  }

  private static void inlineProductInfo(@NotNull Element tipContent) {
    // Inline all custom entities like productName
    for (Element element : tipContent.getElementsContainingOwnText("&")) {
      String text = element.text();
      for (TipEntity entity : ENTITIES) {
        text = entity.inline(text);
      }
      element.text(text);
    }
  }

  private static void handleWarning(@NotNull String message, boolean isStrict) {
    if (isStrict) {
      throw new RuntimeException("Warning: " + message);
    }
    else {
      LOG.warn(message);
    }
  }

  private static void handleError(@NotNull Throwable t, boolean isStrict) {
    if (isStrict) {
      throw new RuntimeException(t);
    }
    else {
      LOG.warn(t);
    }
  }

  private static @NotNull String getCantReadText(@NotNull TipAndTrickBean bean) {
    String plugin = getPoweredByText(bean);
    String product = ApplicationNamesInfo.getInstance().getFullProductName();
    if (!plugin.isEmpty()) {
      product += " and " + plugin + " plugin";
    }
    return IdeBundle.message("error.unable.to.read.tip.of.the.day", bean.fileName, product);
  }

  private static @NlsSafe @NotNull String getPoweredByText(@NotNull TipAndTrickBean tip) {
    PluginDescriptor descriptor = tip.getPluginDescriptor();
    return descriptor == null ||
           PluginManagerCore.CORE_ID.equals(descriptor.getPluginId()) ?
           "" :
           descriptor.getName();
  }

  private static final class TipEntity {
    private final String name;
    private final String value;

    private TipEntity(String name, String value) {
      this.name = name;
      this.value = value;
    }

    String inline(final String where) {
      return where.replace(String.format("&%s;", name), value);
    }

    static TipEntity of(String name, String value) {
      return new TipEntity(name, value);
    }
  }

  private static class TipRetriever {

    private final ClassLoader myLoader;
    private final String myPath;
    private final String mySubPath;

    private TipRetriever(ClassLoader loader, String path, String subPath) {
      myLoader = loader;
      myPath = path;
      mySubPath = subPath;
    }

    @Nullable
    String getTipContent(final @Nullable String tipName) {
      if (tipName == null) return null;
      URL tipUrl = getTipUrl(tipName);
      if (tipUrl != null) {
        try {
          return ResourceUtil.loadText(tipUrl.openStream());
        }
        catch (IOException ignored) {
        }
      }
      return null;
    }

    @Nullable
    URL getTipUrl(final @NotNull String tipName) {
      final String tipLocation = String.format("/%s/%s", myPath, mySubPath.length() > 0 ? mySubPath + "/" : "");
      URL tipUrl = ResourceUtil.getResource(myLoader, tipLocation, tipName);
      //Tip not found, but if its name starts with prefix, try without as a safety measure.
      if (tipUrl == null && tipName.startsWith("neue-")) {
        tipUrl = ResourceUtil.getResource(myLoader, tipLocation, tipName.substring(5));
      }
      return tipUrl;
    }
  }

  static class IconWithRoundedBorder implements Icon {
    private final @NotNull Icon delegate;

    IconWithRoundedBorder(@NotNull Icon delegate) {
      this.delegate = delegate;
    }

    @Override
    public int getIconWidth() {
      return delegate.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return delegate.getIconHeight();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2d = (Graphics2D)g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      float arcSize = JBUIScale.scale(16);
      var clipBounds = new RoundRectangle2D.Float(x, y, getIconWidth(), getIconHeight(), arcSize, arcSize);
      g2d.clip(clipBounds);

      delegate.paintIcon(c, g2d, x, y);

      g2d.setPaint(TipUiSettings.getImageBorderColor());
      g2d.setStroke(new BasicStroke(2f));
      g2d.draw(clipBounds);

      g2d.dispose();
    }
  }
}
