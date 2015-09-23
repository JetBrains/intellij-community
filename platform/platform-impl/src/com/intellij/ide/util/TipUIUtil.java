/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ResourceUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;

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

  public static void openTipInBrowser(String tipFileName, JEditorPane browser, Class providerClass) {
    TipAndTrickBean tip = TipAndTrickBean.findByFileName(tipFileName);
    if (tip == null && StringUtil.isNotEmpty(tipFileName)) {
      tip = new TipAndTrickBean();
      tip.fileName = tipFileName;
    }
    openTipInBrowser(tip, browser);
  }

  public static void openTipInBrowser(@Nullable TipAndTrickBean tip, JEditorPane browser) {
    if (tip == null) return;
    try {
      PluginDescriptor pluginDescriptor = tip.getPluginDescriptor();
      ClassLoader tipLoader = pluginDescriptor == null ? TipUIUtil.class.getClassLoader() :
                              ObjectUtils.notNull(pluginDescriptor.getPluginClassLoader(), TipUIUtil.class.getClassLoader());

      URL url = ResourceUtil.getResource(tipLoader, "/tips/", tip.fileName);

      if (url == null) {
        setCantReadText(browser, tip);
        return;
      }

      StringBuffer text = new StringBuffer(ResourceUtil.loadText(url));
      updateShortcuts(text);
      updateImages(text, tipLoader);
      String replaced = text.toString().replace("&productName;", ApplicationNamesInfo.getInstance().getFullProductName());
      String major = ApplicationInfo.getInstance().getMajorVersion();
      replaced = replaced.replace("&majorVersion;", major);
      String minor = ApplicationInfo.getInstance().getMinorVersion();
      replaced = replaced.replace("&minorVersion;", minor);
      replaced = replaced.replace("&majorMinorVersion;", major + ("0".equals(minor) ? "" : ("." + minor)));
      replaced = replaced.replace("&settingsPath;", CommonBundle.settingsActionPath());
      replaced = replaced.replaceFirst("<link rel=\"stylesheet\".*tips\\.css\">", ""); // don't reload the styles
      if (browser.getUI() == null) {
        browser.updateUI();
        boolean succeed = browser.getUI() != null;
        String message = "reinit JEditorPane.ui: " + (succeed ? "OK" : "FAIL") +
                         ", laf=" + LafManager.getInstance().getCurrentLookAndFeel();
        if (succeed) LOG.warn(message);
        else LOG.error(message);
      }
      adjustFontSize(((HTMLEditorKit)browser.getEditorKit()).getStyleSheet());
      browser.read(new StringReader(replaced), url);
    }
    catch (IOException e) {
      setCantReadText(browser, tip);
    }
  }

  private static final String TIP_HTML_TEXT_TAGS = "h1, p, pre, ul";

  private static void adjustFontSize(StyleSheet styleSheet) {
    int size = (int)UIUtil.getFontSize(UIUtil.FontSize.MINI);
    styleSheet.addRule(TIP_HTML_TEXT_TAGS + " {font-size: " + size + "px;}");
  }

  private static void setCantReadText(JEditorPane browser, TipAndTrickBean bean) {
    try {
      String plugin = getPoweredByText(bean);
      String product = ApplicationNamesInfo.getInstance().getFullProductName();
      if (!plugin.isEmpty()) {
        product += " and " + plugin + " plugin";
      }
      String message = IdeBundle.message("error.unable.to.read.tip.of.the.day", bean.fileName, product);
      browser.read(new StringReader(message), null);
    }
    catch (IOException ignored) {
    }
  }

  private static void updateImages(StringBuffer text, ClassLoader tipLoader) {
    final boolean dark = UIUtil.isUnderDarcula();
    final boolean retina = UIUtil.isRetina();
//    if (!dark && !retina) {
//      return;
//    }

    String suffix = "";
    if (retina) suffix += "@2x";
    if (dark) suffix += "_dark";
    int index = text.indexOf("<img", 0);
    while (index != -1) {
      final int end = text.indexOf(">", index + 1);
      if (end == -1) return;
      final String img = text.substring(index, end + 1).replace('\r', ' ').replace('\n',' ');
      final int srcIndex = img.indexOf("src=");
      final int endIndex = img.indexOf(".png", srcIndex);
      if (endIndex != -1) {
        String path = img.substring(srcIndex + 5, endIndex);
        if (!path.endsWith("_dark") && !path.endsWith("@2x")) {
          path += suffix + ".png";
          URL url = ResourceUtil.getResource(tipLoader, "/tips/", path);
          if (url != null) {
            String newImgTag = "<img src=\"" + path + "\" ";
            if (retina) {
              try {
                final BufferedImage image = ImageIO.read(url.openStream());
                final int w = image.getWidth() / 2;
                final int h = image.getHeight() / 2;
                newImgTag += "width=\"" + w + "\" height=\"" + h + "\"";
              } catch (Exception ignore) {
                newImgTag += "width=\"400\" height=\"200\"";
              }
            }
            newImgTag += "/>";
            text.replace(index, end + 1, newImgTag);
          }
        }
      }
      index = text.indexOf("<img", index + 1);
    }
  }

  private static void updateShortcuts(StringBuffer text) {
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

  @NotNull
  public static JEditorPane createTipBrowser() {
    JEditorPane browser = new JEditorPane();
    browser.setEditable(false);
    browser.setBackground(UIUtil.getTextFieldBackground());
    browser.addHyperlinkListener(
      new HyperlinkListener() {
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            BrowserUtil.browse(e.getURL());
          }
        }
      }
    );
    URL resource = ResourceUtil.getResource(TipUIUtil.class, "/tips/css/", UIUtil.isUnderDarcula() ? "tips_darcula.css" : "tips.css");
    final StyleSheet styleSheet = UIUtil.loadStyleSheet(resource);
    HTMLEditorKit kit = new HTMLEditorKit() {
      @Override
      public StyleSheet getStyleSheet() {
        return styleSheet != null ? styleSheet : super.getStyleSheet();
      }
    };
    browser.setEditorKit(kit);
    return browser;
  }
}
