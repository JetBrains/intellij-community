// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.DynamicBundle;
import com.intellij.featureStatistics.FeatureDescriptor;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.DefaultKeymap;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ResourceUtil;
import com.intellij.util.SVGLoader;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.View;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.DynamicBundle.findLanguageBundle;
import static com.intellij.util.ui.UIUtil.drawImage;

/**
 * @author Konstantin Bulenkov
 */
public final class TipUIUtil {
  private static final Logger LOG = Logger.getInstance(TipUIUtil.class);
  private static final Pattern SHORTCUT_PATTERN = Pattern.compile("&shortcut:([\\w.$]+?);");
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

  public static @NlsSafe @NotNull String getPoweredByText(@NotNull TipAndTrickBean tip) {
    PluginDescriptor descriptor = tip.getPluginDescriptor();
    return descriptor == null ||
           PluginManagerCore.CORE_ID.equals(descriptor.getPluginId()) ?
           "" :
           descriptor.getName();
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

  public static @NlsSafe String getTipText(@Nullable TipAndTrickBean tip, Component component) {
    if (tip == null) return IdeBundle.message("no.tip.of.the.day");
    final String cssFile = StartupUiUtil.isUnderDarcula()
                           ? "css/tips_darcula.css" : "css/tips.css";
    try {
      StringBuilder text = new StringBuilder();
      String cssText = null;
      File tipFile = new File(tip.fileName);
      if (tipFile.isAbsolute() && tipFile.exists()) {
        text.append(FileUtil.loadFile(tipFile, StandardCharsets.UTF_8));
        updateImagesAndEntities(text, null, tipFile.getParentFile().getAbsolutePath(), component);
        cssText = FileUtil.loadFile(new File(tipFile.getParentFile(), cssFile));
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

        String tipContent = null;

        for (final TipRetriever retriever : retrievers) {
          tipContent = retriever.getTipContent(tip.fileName);
          if (tipContent != null) {
            //So one of retrievers finds a tip. Since they're processed in order from first to last,
            //it will look in i18n first, then fallback to english version
            text.append(tipContent);

            final String tipImagesLocation =
              String.format("/%s/%s", retriever.myPath, retriever.mySubPath.length() > 0 ? retriever.mySubPath + "/" : "");

            //Here and onwards we'll use path properties from successful tip retriever to get images and css
            //This new method updates all: images, entities and shortcuts
            updateImagesAndEntities(text, retriever.myLoader, tipImagesLocation, component);
            final InputStream cssResourceStream = ResourceUtil.getResourceAsStream(retriever.myLoader, retriever.myPath, cssFile);
            cssText = cssResourceStream != null ? ResourceUtil.loadText(cssResourceStream) : "";
            break;
          }
        }
        //All retrievers have failed, return error.
        if (tipContent == null) return getCantReadText(tip);
      }
      String replaced = text.toString();

      final String inlinedCSS =
        cssText + "\nbody {background-color:#" + ColorUtil.toHex(UIUtil.getTextFieldBackground()) + ";overflow:hidden;}";
      return replaced.replaceFirst("<link.*\\.css\">", "<style type=\"text/css\">\n" + inlinedCSS + "\n</style>");
    }
    catch (IOException e) {
      return getCantReadText(tip);
    }
  }

  public static void openTipInBrowser(@Nullable TipAndTrickBean tip, TipUIUtil.Browser browser) {
    browser.setText(getTipText(tip, browser.getComponent()));
  }

  private static @NotNull String getCantReadText(@NotNull TipAndTrickBean bean) {
    String plugin = getPoweredByText(bean);
    String product = ApplicationNamesInfo.getInstance().getFullProductName();
    if (!plugin.isEmpty()) {
      product += " and " + plugin + " plugin";
    }
    return IdeBundle.message("error.unable.to.read.tip.of.the.day", bean.fileName, product);
  }

  private static void updateImagesAndEntities(StringBuilder text, ClassLoader tipLoader, String tipPath, Component component) {
    final boolean dark = StartupUiUtil.isUnderDarcula();
    final boolean hidpi = JBUI.isPixHiDPI(component);

    //Let's use JSOUP because normalizing HTML manually is less reliable
    final Document tipHtml = Jsoup.parse(text.toString());
    tipHtml.outputSettings().prettyPrint(false);

    // First, inline all custom entities like productName
    for (Element element : tipHtml.getElementsContainingOwnText("&")) {
      // It's just cleaner expression here that we can configure entities elsewhere and just replace them here in one loop.
      String textNodeText = element.text();
      for (TipEntity entity : ENTITIES) {
        textNodeText = entity.inline(textNodeText);
      }
      element.text(textNodeText);
    }

    // Here comes shortcut processing
    for (Element shortcut : tipHtml.getElementsMatchingOwnText(SHORTCUT_PATTERN)) {
      shortcut.text(SHORTCUT_PATTERN.matcher(shortcut.text()).replaceAll(result -> {
        String shortcutText = null;
        final String actionId = result.group(1);
        if (actionId != null) {
          shortcutText = getShortcutText(actionId, KeymapManager.getInstance().getActiveKeymap());
          if (shortcutText == null) {
            Keymap defKeymap = KeymapManager.getInstance().getKeymap(DefaultKeymap.Companion.getInstance().getDefaultKeymapName());
            if (defKeymap != null) {
              shortcutText = getShortcutText(actionId, defKeymap);
              if (shortcutText != null) {
                shortcutText += IdeBundle.message("tips.of.the.day.shortcut.default.keymap");
              }
            }
          }
        }
        if (shortcutText == null) {
          shortcutText = "\"" + actionId + "\" " + IdeBundle.message("tips.of.the.day.shortcut.must.define");
        }
        return Matcher.quoteReplacement(shortcutText);
      }));
    }

    //And finally images
    tipHtml.getElementsByTag("img")
      .forEach(img -> {

        final String src = img.attributes().getIgnoreCase("src");
        final TodImage originalImage = TodImage.of(src);
        final TodImage baseImage = originalImage.base();
        //Here we're preparing the list of images in the order of their preference.
        //We need to be thorough and account for all possible combinations
        final ArrayList<TodImage> imagesToTryLight = new ArrayList<>();
        final ArrayList<TodImage> imagesToTryDark = new ArrayList<>();

        imagesToTryLight.add(baseImage);
        imagesToTryLight.add(baseImage.retina());

        imagesToTryDark.add(baseImage.dark());
        imagesToTryDark.add(baseImage.dark().retina());

        if (hidpi) {


          Collections.reverse(imagesToTryLight);
          Collections.reverse(imagesToTryDark);
        }
        final ArrayList<TodImage> imagesToTry = new ArrayList<>();
        if (dark) {
          imagesToTry.addAll(imagesToTryDark);
          imagesToTry.addAll(imagesToTryLight);
        }
        else {
          imagesToTry.addAll(imagesToTryLight);
          imagesToTry.addAll(imagesToTryDark);
        }
        //By this point we have all the possible images to try out;
        // that's our fallback in case someone did some weird combination of src attribute/actual images in bundle
        try {
          BufferedImage image = null;
          boolean fallbackUpscale = false;
          boolean fallbackDownscale = false;
          URL actualURL = null;

          for (final TodImage i : imagesToTry) {
            try {
              actualURL = new URL(getImageCanonicalPath(i.toString(), tipLoader, tipPath));
              image = read(actualURL);
            }
            catch (IOException ignored) {
            }
            //Found something, look no further
            if (image != null) {
              if (hidpi) {
                fallbackUpscale = originalImage.isRetina() && !i.isRetina();
              }
              else {
                fallbackDownscale = !originalImage.isRetina() && i.isRetina();
              }
              break;
            }
          }
          //Let's not ignore author specified values here
          int w = intValueOf(img.attributes().getIgnoreCase("width"), image.getWidth());
          if (hidpi) {
            // the expected (user space) size is @2x / 2 in either JRE-HiDPI or IDE-HiDPI mode
            float k = 2f;
            if (StartupUiUtil.isJreHiDPI(component)) {
              // in JRE-HiDPI mode we want the image to be drawn in its original size w/h, for better quality
              k = JBUIScale.sysScale(component);
            }
            w /= k;
          }
          // round the user scale for better quality
          int userScale = RoundingMode.ROUND_FLOOR_BIAS.round(JBUIScale.scale(1f));
          w = userScale * w;
          if (fallbackUpscale) {
            w *= 2;
          }
          else if (fallbackDownscale) {
            w /= 2;
          }
          //Actually we don't need height, let the browser take care of that
          img.attr("src", actualURL.toExternalForm());
          img.attr("width", String.valueOf(w));
        }
        catch (Exception e) {
          LOG.warn("ToD: cannot load image [" + src + "]", e);
        }
      });
    text.replace(0, text.length(), tipHtml.outerHtml());
  }

  private static int intValueOf(final String raw, int substitute) {
    try {
      return Integer.parseInt(raw);
    }
    catch (NumberFormatException ignore) {
    }
    return substitute;
  }

  private static @NotNull String getImageCanonicalPath(@NotNull String path, @Nullable ClassLoader tipLoader, @NotNull String tipPath) {
    try {
      URL url = tipLoader == null ? new File(tipPath, path).toURI().toURL() : ResourceUtil.getResource(tipLoader, tipPath, path);
      return url == null ? path : url.toExternalForm();
    }
    catch (MalformedURLException e) {
      return path;
    }
  }

  private static BufferedImage read(@NotNull URL url) throws IOException {
    try (InputStream stream = url.openStream()) {
      BufferedImage image = ImageIO.read(stream);
      if (image == null) throw new IOException("Cannot read image with ImageIO: " + url.toExternalForm());
      return image;
    }
  }

  private static @Nullable String getShortcutText(String actionId, Keymap keymap) {
    for (final Shortcut shortcut : keymap.getShortcuts(actionId)) {
      if (shortcut instanceof KeyboardShortcut) {
        return KeymapUtil.getShortcutText(shortcut);
      }
    }
    return null;
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
        .withViewFactoryExtensions(getSVGImagesExtension())
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

  static final class TodImage {
    static final String RETINA_SUFFIX = "@2x";
    static final String DARK_SUFFIX = "_dark";

    private final String name;
    private final String extension;
    private final String defaultExtension;
    private final boolean dark;
    private final boolean retina;

    private TodImage(final @NotNull String name) {

      final CharSequence tempExtension = FileUtilRt.getExtension(name, "");

      extension = tempExtension.toString();
      defaultExtension = tempExtension.toString();

      String tempName = FileUtil.getNameWithoutExtension(name);

      if (tempName.endsWith(DARK_SUFFIX)) {
        dark = true;
        tempName = StringUtil.substringBeforeLast(name, DARK_SUFFIX);
      }
      else {
        dark = false;
      }

      if (tempName.endsWith(RETINA_SUFFIX)) {
        retina = true;
        tempName = StringUtil.substringBeforeLast(name, RETINA_SUFFIX);
      }
      else {
        retina = false;
      }

      this.name = tempName;
    }

    private TodImage(String name,
                     String extension,
                     boolean dark,
                     boolean retina) {
      this(name, extension, dark, retina, extension);
    }

    private TodImage(String name,
                     String extension,
                     boolean dark,
                     boolean retina,
                     String defaultExtension) {

      this.name = name;
      this.extension = extension;
      this.defaultExtension = defaultExtension;
      this.dark = dark;
      this.retina = retina;
    }

    public @NotNull String getName() {
      return name;
    }

    public @NotNull String getExtension() {
      return extension;
    }

    public boolean isDark() {
      return dark;
    }

    public boolean isRetina() {
      return retina;
    }

    public @NotNull TipUIUtil.TodImage dark() {
      return new TodImage(name, extension, true, retina);
    }

    public @NotNull TipUIUtil.TodImage nonDark() {
      return new TodImage(name, extension, false, retina);
    }

    public @NotNull TipUIUtil.TodImage retina() {
      return new TodImage(name, extension, dark, true);
    }

    public @NotNull TipUIUtil.TodImage nonRetina() {
      return new TodImage(name, extension, dark, false);
    }

    public @NotNull TipUIUtil.TodImage withExtension(final @NotNull String extension) {
      return new TodImage(name, extension, dark, retina);
    }

    public @NotNull TipUIUtil.TodImage withName(final @NotNull String name) {
      return new TodImage(name, extension, dark, retina);
    }

    public @NotNull TipUIUtil.TodImage base() {
      return new TodImage(name, defaultExtension, false, false);
    }

    @Override
    public String toString() {
      return name +
             (retina ? RETINA_SUFFIX : "") +
             (dark ? DARK_SUFFIX : "") +
             "." +
             extension;
    }

    public static @NotNull TipUIUtil.TodImage of(final @NotNull String src) {
      return new TodImage(src);
    }
  }
}