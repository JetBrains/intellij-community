// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.*;
import javax.swing.text.html.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

public class JBHtmlEditorKit extends HTMLEditorKit {
  static {
    StartupUiUtil.configureHtmlKitStylesheet();
  }

  @Override
  public Cursor getDefaultCursor() {
    return null;
  }

  private final StyleSheet style;
  private final HyperlinkListener myHyperlinkListener;

  public JBHtmlEditorKit() {
    this(true);
  }

  public JBHtmlEditorKit(boolean noGapsBetweenParagraphs) {
    style = createStyleSheet();
    if (noGapsBetweenParagraphs) style.addRule("p { margin-top: 0; }");
    myHyperlinkListener = new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        Element element = e.getSourceElement();
        if (element == null) return;
        if (element.getName().equals("img")) return;

        if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
          setUnderlined(true, element);
        } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
          setUnderlined(false, element);
        }
      }

      private void setUnderlined(boolean underlined, @NotNull Element element) {
        AttributeSet attributes = element.getAttributes();
        Object attribute = attributes.getAttribute(HTML.Tag.A);
        if (attribute instanceof MutableAttributeSet) {
          MutableAttributeSet a = (MutableAttributeSet)attribute;
          a.addAttribute(CSS.Attribute.TEXT_DECORATION, underlined ? "underline" : "none");
          ((StyledDocument)element.getDocument()).setCharacterAttributes(element.getStartOffset(),
                                                                         element.getEndOffset() - element.getStartOffset(),
                                                                         a, false);
        }
      }
    };
  }

  @Override
  public StyleSheet getStyleSheet() {
    return style;
  }

  @Override
  public Document createDefaultDocument() {
    StyleSheet styles = getStyleSheet();
    // static class instead anonymous for exclude $this [memory leak]
    StyleSheet ss = new StyleSheetCompressionThreshold();
    ss.addStyleSheet(styles);

    HTMLDocument doc = new HTMLDocument(ss);
    doc.setParser(getParser());
    doc.setAsynchronousLoadPriority(4);
    doc.setTokenThreshold(100);
    return doc;
  }

  public static StyleSheet createStyleSheet() {
    StyleSheet style = new StyleSheet();
    style.addStyleSheet(StartupUiUtil.isUnderDarcula() ? (StyleSheet)UIManager.getDefaults().get("StyledEditorKit.JBDefaultStyle") : StartupUiUtil.getDefaultHtmlKitCss());
    style.addRule("code { font-size: 100%; }"); // small by Swing's default
    style.addRule("small { font-size: small; }"); // x-small by Swing's default
    style.addRule("a { text-decoration: none;}");
    // override too large default margin "ul {margin-left-ltr: 50; margin-right-rtl: 50}" from javax/swing/text/html/default.css
    style.addRule("ul { margin-left-ltr: 10; margin-right-rtl: 10; }");
    // override too large default margin "ol {margin-left-ltr: 50; margin-right-rtl: 50}" from javax/swing/text/html/default.css
    // Select ol margin to have the same indentation as "ul li" and "ol li" elements (seems value 22 suites well)
    style.addRule("ol { margin-left-ltr: 22; margin-right-rtl: 22; }");

    return style;
  }

  @Override
  public void install(final JEditorPane pane) {
    super.install(pane);
    // JEditorPane.HONOR_DISPLAY_PROPERTIES must be set after HTMLEditorKit is completely installed
    pane.addPropertyChangeListener("editorKit", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent e) {
        // In case JBUI user scale factor changes, the font will be auto-updated by BasicTextUI.installUI()
        // with a font of the properly scaled size. And is then propagated to CSS, making HTML text scale dynamically.

        // The default JEditorPane's font is the label font, seems there's no need to reset it here.
        // If the default font is overridden, more so we should not reset it.
        // However, if the new font is not UIResource - it won't be auto-scaled.
        // [tav] dodo: remove the next two lines in case there're no regressions
        //Font font = getLabelFont();
        //pane.setFont(font);

        // let CSS font properties inherit from the pane's font
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.removePropertyChangeListener(this);
      }
    });
    pane.addHyperlinkListener(myHyperlinkListener);

    java.util.List<LinkController> listeners1 = filterLinkControllerListeners(pane.getMouseListeners());
    java.util.List<LinkController> listeners2 = filterLinkControllerListeners(pane.getMouseMotionListeners());
    // replace just the original listener
    if (listeners1.size() == 1 && listeners1.equals(listeners2)) {
      LinkController oldLinkController = listeners1.get(0);
      pane.removeMouseListener(oldLinkController);
      pane.removeMouseMotionListener(oldLinkController);
      MouseExitSupportLinkController newLinkController = new MouseExitSupportLinkController();
      pane.addMouseListener(newLinkController);
      pane.addMouseMotionListener(newLinkController);
    }
  }

  @NotNull
  private static List<LinkController> filterLinkControllerListeners(@NotNull Object[] listeners) {
    return ContainerUtil.mapNotNull(listeners, o -> ObjectUtils.tryCast(o, LinkController.class));
  }

  @Override
  public void deinstall(@NotNull JEditorPane c) {
    c.removeHyperlinkListener(myHyperlinkListener);
    super.deinstall(c);
  }

  private static class StyleSheetCompressionThreshold extends StyleSheet {
    @Override
    protected int getCompressionThreshold() {
      return -1;
    }
  }

  // Workaround for https://bugs.openjdk.java.net/browse/JDK-8202529
  private static class MouseExitSupportLinkController extends HTMLEditorKit.LinkController {
    @Override
    public void mouseExited(@NotNull MouseEvent e) {
      mouseMoved(new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiersEx(), -1, -1, e.getClickCount(), e.isPopupTrigger(), e.getButton()));
    }
  }
}
