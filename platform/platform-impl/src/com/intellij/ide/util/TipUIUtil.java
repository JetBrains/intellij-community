// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.DynamicBundle;
import com.intellij.featureStatistics.FeatureDescriptor;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.text.paragraph.TextParagraph;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.icons.LoadIconParameters;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.ResourceUtil;
import com.intellij.util.SVGLoader;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ui.*;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;

import static com.intellij.DynamicBundle.findLanguageBundle;
import static com.intellij.util.ImageLoader.*;
import static com.intellij.util.ui.UIUtil.drawImage;

/**
 * @author Konstantin Bulenkov
 */
public final class TipUIUtil {
  private static final Logger LOG = Logger.getInstance(TipUIUtil.class);
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

  private TipUIUtil() {
  }

  public static @Nullable TipAndTrickBean getTip(@Nullable FeatureDescriptor feature) {
    if (feature == null) {
      return null;
    }
    String tipFileName = feature.getTipFileName();
    if (tipFileName == null) {
      LOG.warn("No Tip of the day for feature " + feature.getId());
      return null;
    }

    TipAndTrickBean tip = TipAndTrickBean.findByFileName("neue-" + tipFileName);
    if (tip == null && StringUtil.isNotEmpty(tipFileName)) {
      tip = TipAndTrickBean.findByFileName(tipFileName);
    }
    if (tip == null && StringUtil.isNotEmpty(tipFileName)) {
      tip = new TipAndTrickBean();
      tip.fileName = tipFileName;
    }
    return tip;
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
        final ClassLoader fallbackLoader = TipUIUtil.class.getClassLoader();
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

  public static Browser createBrowser() {
    return new SwingBrowser();
  }

  public interface Browser extends TextAccessor {
    void load(String url) throws IOException;

    JComponent getComponent();

    @Override
    void setText(@Nls String text);
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
      String result = null;
      if (tipName != null) {
        final String tipLocation = String.format("/%s/%s", myPath, mySubPath.length() > 0 ? mySubPath + "/" : "");
        InputStream tipStream =
          ResourceUtil.getResourceAsStream(myLoader, tipLocation, tipName);
        //Tip not found, but if its name starts with prefix, try without as a safety measure.
        if (tipStream == null && tipName.startsWith("neue-")) {
          tipStream = ResourceUtil.getResourceAsStream(myLoader, tipLocation, tipName.substring(5));
        }
        if (tipStream != null) {
          try {
            result = ResourceUtil.loadText(tipStream);
          }
          catch (IOException ignored) {
          }
        }
      }
      return result;
    }
  }

  private static final class SwingBrowser extends JEditorPane implements Browser {
    SwingBrowser() {
      setEditable(false);
      setBackground(UIUtil.getTextFieldBackground());
      addHyperlinkListener(
        new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              BrowserUtil.browse(e.getURL());
            }
          }
        }
      );

      HTMLEditorKit kit = new HTMLEditorKitBuilder()
        .replaceViewFactoryExtensions(getSVGImagesExtension())
        .withGapsBetweenParagraphs()
        .build();

      String fileName = "tips/css/" + (StartupUiUtil.isUnderDarcula() ? "tips_darcula.css" : "tips.css");
      try {
        byte[] data = ResourceUtil.getResourceAsBytes(fileName, TipUIUtil.class.getClassLoader());
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          LOG.assertTrue(data != null);
        }
        if (data != null) {
          kit.getStyleSheet().addStyleSheet(StyleSheetUtil.loadStyleSheet(new ByteArrayInputStream(data)));
        }
      }
      catch (IOException e) {
        LOG.error("Cannot load stylesheet " + fileName, e);
      }
      setEditorKit(kit);
    }

    private @NotNull ExtendableHTMLViewFactory.Extension getSVGImagesExtension() {
      return (elem, view) -> {
        if (!(view instanceof ImageView)) return null;
        String src = (String)view.getElement().getAttributes().getAttribute(HTML.Attribute.SRC);
        if (src != null /*&& src.endsWith(".svg")*/) {
          final Image image;
          try {
            final URL url = new URL(src);
            Dictionary cache = (Dictionary)elem.getDocument().getProperty("imageCache");
            if (cache == null) {
              elem.getDocument().putProperty("imageCache", cache = new Dictionary() {
                private final HashMap myMap = new HashMap();

                @Override
                public int size() {
                  return myMap.size();
                }

                @Override
                public boolean isEmpty() {
                  return size() == 0;
                }

                @Override
                public Enumeration keys() {
                  return Collections.enumeration(myMap.keySet());
                }

                @Override
                public Enumeration elements() {
                  return Collections.enumeration(myMap.values());
                }

                @Override
                public Object get(Object key) {
                  return myMap.get(key);
                }

                @Override
                public Object put(Object key, Object value) {
                  return myMap.put(key, value);
                }

                @Override
                public Object remove(Object key) {
                  return myMap.remove(key);
                }
              });
            }
            image = src.endsWith(".svg")
                    ? SVGLoader.load(url, JBUI.isPixHiDPI((Component)null) ? 2f : 1f)
                    : Toolkit.getDefaultToolkit().createImage(url);
            cache.put(url, image);
            if (src.endsWith(".svg")) {
              return new ImageView(elem) {
                @Override
                public Image getImage() {
                  return image;
                }

                @Override
                public URL getImageURL() {
                  return url;
                }

                @Override
                public void paint(Graphics g, Shape a) {
                  Rectangle bounds = a.getBounds();
                  int width = (int)getPreferredSpan(View.X_AXIS);
                  int height = (int)getPreferredSpan(View.Y_AXIS);
                  @SuppressWarnings("UndesirableClassUsage")
                  BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                  Graphics2D graphics = buffer.createGraphics();
                  super.paint(graphics, new Rectangle(buffer.getWidth(), buffer.getHeight()));
                  drawImage(g, ImageUtil.ensureHiDPI(image, ScaleContext.create((Component)null)), bounds.x, bounds.y, null);
                }

                @Override
                public float getMaximumSpan(int axis) {
                  return getPreferredSpan(axis);
                }

                @Override
                public float getMinimumSpan(int axis) {
                  return getPreferredSpan(axis);
                }

                @Override
                public float getPreferredSpan(int axis) {
                  return (axis == View.X_AXIS ? image.getWidth(null) : image.getHeight(null)) / JBUIScale.sysScale();
                }
              };
            }
          }
          catch (IOException e) {
            //ignore
          }
        }

        return null;
      };
    }

    @Override
    public void setText(String t) {
      super.setText(t);
      if (t != null && t.length() > 0) {
        setCaretPosition(0);
      }
    }

    @Override
    public void load(String url) throws IOException {
      @NlsSafe String text = IOUtil.readString(new DataInputStream(new URL(url).openStream()));
      setText(text);
    }

    @Override
    public JComponent getComponent() {
      return this;
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