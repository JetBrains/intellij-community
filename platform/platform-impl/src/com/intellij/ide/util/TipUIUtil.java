// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.DefaultKeymap;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ResourceUtil;
import com.intellij.util.SVGLoader;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.twelvemonkeys.imageio.stream.ByteArrayImageInputStream;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.intellij.util.ui.UIUtil.drawImage;
import static com.intellij.util.ui.UIUtil.isUnderDarcula;

/**
 * @author dsl
 * @author Konstantin Bulenkov
 */
public class TipUIUtil {
  private static final Logger LOG = Logger.getInstance(TipUIUtil.class);
  private static final String SHORTCUT_ENTITY = "&shortcut:";

  private TipUIUtil() {
  }

  @NotNull
  public static String getPoweredByText(@NotNull TipAndTrickBean tip) {
    PluginDescriptor descriptor = tip.getPluginDescriptor();
    return descriptor instanceof IdeaPluginDescriptor &&
           !PluginManagerCore.CORE_PLUGIN_ID.equals(descriptor.getPluginId().getIdString()) ?
           ((IdeaPluginDescriptor)descriptor).getName() : "";
  }

  @Nullable
  public static TipAndTrickBean getTip(String tipFileName) {
    TipAndTrickBean tip = TipAndTrickBean.findByFileName(tipFileName);
    if (tip == null && StringUtil.isNotEmpty(tipFileName)) {
      tip = new TipAndTrickBean();
      tip.fileName = tipFileName;
    }
    return tip;
  }

  @Deprecated
  public static void openTipInBrowser(@Nullable TipAndTrickBean tip, JEditorPane browser) {
    browser.setText(getTipText(tip, browser));
  }

  private static String getTipText(@Nullable TipAndTrickBean tip, Component component) {
    if (tip == null) return "";
    try {
      StringBuilder text = new StringBuilder();
      String cssText;
      File tipFile = new File(tip.fileName);
      if (tipFile.isAbsolute() && tipFile.exists()) {
        text.append(FileUtil.loadFile(tipFile));
        updateImages(text, null, tipFile.getParentFile().getAbsolutePath(), component);
        cssText = FileUtil.loadFile(new File(tipFile.getParentFile(), isUnderDarcula() ? "css/tips_darcula.css" : "css/tips.css"));
      }
      else {
        PluginDescriptor pluginDescriptor = tip.getPluginDescriptor();
        ClassLoader tipLoader = pluginDescriptor == null ? TipUIUtil.class.getClassLoader() :
                                ObjectUtils.notNull(pluginDescriptor.getPluginClassLoader(), TipUIUtil.class.getClassLoader());

        URL url = ResourceUtil.getResource(tipLoader, "/tips/", tip.fileName);
        if (url == null) {
          return getCantReadText(tip);
        }
        text.append(ResourceUtil.loadText(url));
        updateImages(text, tipLoader, "", component);
        URL cssResource = ResourceUtil.getResource(tipLoader, "/tips/", isUnderDarcula() ? "css/tips_darcula.css" : "css/tips.css");
        cssText = cssResource != null ? new String(readBytes(cssResource), StandardCharsets.UTF_8) : "";
      }

      updateShortcuts(text);
      String replaced = text.toString().replace("&productName;", ApplicationNamesInfo.getInstance().getFullProductName());
      String major = ApplicationInfo.getInstance().getMajorVersion();
      replaced = replaced.replace("&majorVersion;", major);
      String minor = ApplicationInfo.getInstance().getMinorVersion();
      replaced = replaced.replace("&minorVersion;", minor);
      replaced = replaced.replace("&majorMinorVersion;", major + ("0".equals(minor) ? "" : ("." + minor)));
      replaced = replaced.replace("&settingsPath;", CommonBundle.settingsActionPath());
      String inlinedCSS = cssText + "\nbody {background-color:#" + ColorUtil.toHex(UIUtil.getTextFieldBackground())+ ";overflow:hidden;}";
      replaced = replaced.replaceFirst("<link.*\\.css\">", "<style type=\"text/css\">\n" + inlinedCSS + "\n</style>");
      return replaced;
    }
    catch (IOException e) {
      return getCantReadText(tip);
    }

  }

  public static void openTipInBrowser(@Nullable TipAndTrickBean tip, TipUIUtil.Browser browser) {
    browser.setText(getTipText(tip, browser.getComponent()));
  }

  private static String getCantReadText(TipAndTrickBean bean) {
    String plugin = getPoweredByText(bean);
    String product = ApplicationNamesInfo.getInstance().getFullProductName();
    if (!plugin.isEmpty()) {
      product += " and " + plugin + " plugin";
    }
    return IdeBundle.message("error.unable.to.read.tip.of.the.day", bean.fileName, product);
  }

  private static void updateImages(StringBuilder text, ClassLoader tipLoader, String tipPath, Component component) {
    final boolean dark = isUnderDarcula();

    int index = text.indexOf("<img", 0);
    while (index != -1) {
      final int end = text.indexOf(">", index + 1);
      if (end == -1) return;
      final String img = text.substring(index, end + 1).replace('\r', ' ').replace('\n',' ');
      int srcIndex = img.indexOf("src=\"");
      int endIndex = img.indexOf("\"", srcIndex + 6);
      if (srcIndex == -1 && endIndex == -1) {
        srcIndex = img.indexOf("src=\'");
        endIndex = img.indexOf("\'", srcIndex + 6);
      }
      if (endIndex != -1) {
        String path = img.substring(srcIndex + 5, endIndex);
        URL url;
        try {
          url = tipLoader != null ? ResourceUtil.getResource(tipLoader, "/tips/", path) : new File(tipPath, path).toURI().toURL();
        }
        catch (MalformedURLException e) {
          url = null;
        }
        if (url != null) {
          path = url.toExternalForm();
        }
        int extPoint = path.lastIndexOf('.');
        String pathWithoutExtension = extPoint != -1 ? path.substring(0, extPoint) : path;
        String fileExtension = extPoint != -1 ? path.substring(extPoint) : "";
        if (!pathWithoutExtension.endsWith("_dark") && !pathWithoutExtension.endsWith("@2x") || tipLoader == null) {
          boolean hidpi =  JBUI.isPixHiDPI(component);
          path = pathWithoutExtension + (hidpi ? "@2x" : "") + (dark ? "_dark" : "") + fileExtension;
          if (url != null) {
            String newImgTag = "<img src=\""+url+"\" ";//stub
            try {
              Trinity<String, BufferedImage, byte[]> trinity;
              boolean fallbackUpscale = false;
              URL actualURL;
              try {
                actualURL = new URL(path);
                trinity = read(actualURL);
              }
              catch (IOException e) {
                if (!path.endsWith(".svg")) {
                  LOG.warn("Cannot find icon with path [" + path + "]");
                }
                fallbackUpscale = hidpi;
                actualURL = url;
                trinity = read(url);
              }
              if (Registry.is("ide.javafx.tips")) {
                newImgTag =
                  "<img src=\"data:image/" + trinity.first + ";base64," + Base64.getEncoder().encodeToString(trinity.third) + "\" ";
              } else {
                newImgTag = "<img src=\""+actualURL.toExternalForm()+"\" ";
              }
              BufferedImage image = trinity.second;
              int w = image.getWidth();
              int h = image.getHeight();
              if (hidpi) {
                // the expected (user space) size is @2x / 2 in either JRE-HiDPI or IDE-HiDPI mode
                float k = 2f;
                if (UIUtil.isJreHiDPI(component)) {
                  // in JRE-HiDPI mode we want the image to be drawn in its original size w/h, for better quality
                  k = JBUI.sysScale(component);
                }
                w /= k;
                h /= k;
              }
              // round the user scale for better quality
              int userScale = RoundingMode.ROUND_FLOOR_BIAS.round(JBUI.scale(1f));
              w = userScale * w;
              h = userScale * h;
              if (fallbackUpscale) {
                w *= 2;
                h *= 2;
              }
              newImgTag += "width=\"" + w + "\" height=\"" + h + "\"";
            } catch (Exception ignore) {
              //newImgTag += "width=\"400\" height=\"200\"";
            }
            newImgTag += ">";
            text.replace(index, end + 1, newImgTag);
          }
        }
      }
      index = text.indexOf("<img", index + 1);
    }
  }

  private static Trinity<String, BufferedImage, byte[]> read(@NotNull URL url) throws IOException {
    byte[] bytes = readBytes(url);
    Iterator<ImageReader> readers = ImageIO.getImageReaders(new ByteArrayImageInputStream(bytes));
    String formatName = "png";
    if (readers.hasNext()) {
      formatName = readers.next().getFormatName();
    }

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
    if (image == null) throw new IOException("Cannot read image with ImageIO: " + url.toExternalForm());
    return Trinity.create(formatName, image, bytes);
  }

  private static byte[] readBytes(@NotNull URL url) throws IOException{
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[16384];
    try (InputStream stream = url.openStream()) {
      for (int len = stream.read(buffer); len > 0; len = stream.read(buffer)) {
        baos.write(buffer, 0, len);
      }
      return baos.toByteArray();
    }
  }

  private static void updateShortcuts(StringBuilder text) {
    int lastIndex = 0;
    while(true) {
      lastIndex = text.indexOf(SHORTCUT_ENTITY, lastIndex);
      if (lastIndex < 0) return;
      final int actionIdStart = lastIndex + SHORTCUT_ENTITY.length();
      int actionIdEnd = text.indexOf(";", actionIdStart);
      if (actionIdEnd < 0) {
        return;
      }
      final String actionId = text.substring(actionIdStart, actionIdEnd);
      String shortcutText = getShortcutText(actionId, KeymapManager.getInstance().getActiveKeymap());
      if (shortcutText == null) {
        Keymap defKeymap = KeymapManager.getInstance().getKeymap(DefaultKeymap.getInstance().getDefaultKeymapName());
        if (defKeymap != null) {
          shortcutText = getShortcutText(actionId, defKeymap);
          if (shortcutText != null) {
            shortcutText += " in default keymap";
          }
        }
      }
      if (shortcutText == null) {
        shortcutText = "<no shortcut for action " + actionId + ">";
      }
      text.replace(lastIndex, actionIdEnd + 1, shortcutText);
      lastIndex += shortcutText.length();
    }
  }

  @Nullable
  private static String getShortcutText(String actionId, Keymap keymap) {
    for (final Shortcut shortcut : keymap.getShortcuts(actionId)) {
      if (shortcut instanceof KeyboardShortcut) {
        return KeymapUtil.getShortcutText(shortcut);
      }
    }
    return null;
  }

  @Deprecated
  @NotNull
  public static JEditorPane createTipBrowser() {
    return new SwingBrowser();
  }

  public static Browser createBrowser() {
    return Registry.is("ide.javafx.tips") ? new JFXBrowser() : new SwingBrowser();
  }

  public interface Browser extends TextAccessor {
    void load(String url) throws IOException;
    JComponent getComponent();
  }

  private static class SwingBrowser extends JEditorPane implements Browser {
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
      URL resource = ResourceUtil.getResource(TipUIUtil.class, "/tips/css/", isUnderDarcula() ? "tips_darcula.css" : "tips.css");
      HTMLEditorKit kit = new UIUtil.JBHtmlEditorKit(false) {
        private final ViewFactory myFactory = createViewFactory();
        //SVG support
        private ViewFactory createViewFactory() {
          return new HTMLEditorKit.HTMLFactory() {
            @Override
            public View create(Element elem) {
              View view = super.create(elem);
              if (view instanceof ImageView) {
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
                          return size() ==0;
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
                    if (src.endsWith(".svg"))
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
                          drawImage(g, ImageUtil.ensureHiDPI(image, JBUI.ScaleContext.create((Component)null)), bounds.x, bounds.y, null);
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
                          return (axis == View.X_AXIS ? image.getWidth(null) : image.getHeight(null))/ JBUI.sysScale();
                        }
                      };
                  }
                  catch (IOException e) {
                    //ignore
                  }
                }
              }
              return view;
            }
          };
        }

        @Override
        public ViewFactory getViewFactory() {
          return myFactory;
        }
      };
      kit.getStyleSheet().addStyleSheet(UIUtil.loadStyleSheet(resource));
      setEditorKit(kit);
    }

    @Override
    public void setText(String t) {
      super.setText(t);
      if (t != null && t.length()>0) {
        setCaretPosition(0);
      }
    }

    @Override
    public void load(String url) throws IOException{
      setText(IOUtil.readString(new DataInputStream(new URL(url).openStream())));
    }

    @Override
    public JComponent getComponent() {
      return this;
    }
  }

  private static class JFXBrowser extends JPanel implements Browser {
    private final JFXPanel myPanel;
    private WebView myWebView;
    private String myRecentText = "";

    JFXBrowser() {
      setLayout(new GridLayout(1, 1));
      setBackground(UIUtil.getTextFieldBackground());
      Long mask = ReflectionUtil.getField(Component.class, this, long.class, "eventMask");
      add(myPanel = new JFXPanel(){
        {if (mask != null) enableEvents(mask);}
      });
      Platform.runLater(() -> {
        Platform.setImplicitExit(false);
        myPanel.addMouseWheelListener(new MouseWheelListener() {
          @Override
          public void mouseWheelMoved(MouseWheelEvent e) {
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(() -> JFXBrowser.this.dispatchEvent(e));
          }
        });
        myPanel.setScene(new Scene(myWebView = new WebView(), 600, 400));
        myWebView.getEngine().getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
          @Override
          public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
            if (newValue == Worker.State.SUCCEEDED) {
              int height = 0;
              int width = 0;
              Integer size = (Integer)myWebView.getEngine().executeScript("document.body.children.length");
              for (int i = 0; i < size; i++) {
                Object w = myWebView.getEngine().executeScript("document.body.children[" + i + "].scrollWidth");
                if (w instanceof Integer) {
                  width = Math.max(width, (Integer)w);
                }
                Object h = myWebView.getEngine().executeScript("document.body.children[" + i + "].scrollHeight");
                if (h instanceof Integer) {
                  height += (Integer)h;
                }
              }
              myPanel.setPreferredSize(new Dimension(width, height));
              myPanel.revalidate();
            }
          }
        });
      });
    }

    @Override
    public void setText(String html) {
      myRecentText = html;
      Platform.runLater(() -> myWebView.getEngine().loadContent(html));
    }

    @Override
    public void load(String url) throws IOException {
      setText(IOUtil.readString(new DataInputStream(new URL(url).openStream())));
    }

    @Override
    public JComponent getComponent() {
      return this;
    }

    @Override
    public String getText() {
      return myRecentText;
    }
  }
}
