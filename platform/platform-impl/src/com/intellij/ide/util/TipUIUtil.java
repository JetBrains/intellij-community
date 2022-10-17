// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ResourceUtil;
import com.intellij.util.SVGLoader;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.View;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;

import static com.intellij.util.ui.UIUtil.drawImage;

/**
 * @deprecated Consider using {@link com.intellij.ide.ui.text.StyledTextPane} for custom text formatting
 */
@Deprecated
public final class TipUIUtil {
  private static final Logger LOG = Logger.getInstance(TipUIUtil.class);

  public static Browser createBrowser() {
    return new SwingBrowser();
  }

  public interface Browser extends TextAccessor {
    void load(String url) throws IOException;

    JComponent getComponent();

    @Override
    void setText(@Nls String text);
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
}