// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import kotlin.jvm.functions.Function0;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
        .replaceViewFactoryExtensions(new SvgImageExtension())
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

    @Override
    public void setText(String t) {
      super.setText(t);
      if (t != null && !t.isEmpty()) {
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