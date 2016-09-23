/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.getLabelFont;
import static com.intellij.util.ui.UIUtil.isUnderDarcula;

/**
 * @author Dmitry Avdeev
 */
public class HTMLEditorKitProvider {
  private static final Logger LOG = Logger.getInstance(HTMLEditorKitProvider.class);
  private static final StyleSheet DEFAULT_HTML_KIT_CSS;

  static {
    blockATKWrapper();
    // save the default JRE CSS and ..
    HTMLEditorKit kit = new HTMLEditorKit();
    DEFAULT_HTML_KIT_CSS = kit.getStyleSheet();
    // .. erase global ref to this CSS so no one can alter it
    kit.setStyleSheet(null);
  }

  private static void blockATKWrapper() {
    /*
     * The method should be called before java.awt.Toolkit.initAssistiveTechnologies()
     * which is called from Toolkit.getDefaultToolkit().
     */
    if (!(SystemInfo.isLinux && Registry.is("linux.jdk.accessibility.atkwrapper.block"))) return;

    if (ScreenReader.isEnabled(ScreenReader.ATK_WRAPPER)) {
      // Replace AtkWrapper with a dummy Object. It'll be instantiated & GC'ed right away, a NOP.
      System.setProperty("javax.accessibility.assistive_technologies", "java.lang.Object");
      LOG.info(ScreenReader.ATK_WRAPPER + " is blocked, see IDEA-149219");
    }
  }

  public static HTMLEditorKit getHTMLEditorKit(boolean noGapsBetweenParagraphs) {
    Font font = getLabelFont();
    @NonNls String family = !SystemInfo.isWindows && font != null ? font.getFamily() : "Tahoma";
    int size = font != null ? font.getSize() : JBUI.scale(11);

    String customCss = String.format("body, div, p { font-family: %s; font-size: %s; }", family, size);
    if (noGapsBetweenParagraphs) {
      customCss += " p { margin-top: 0; }";
    }

    final StyleSheet style = new StyleSheet();
    style.addStyleSheet(isUnderDarcula() ? (StyleSheet)UIManager.getDefaults().get("StyledEditorKit.JBDefaultStyle") : DEFAULT_HTML_KIT_CSS);
    style.addRule(customCss);

    return new HTMLEditorKit() {
      @Override
      public StyleSheet getStyleSheet() {
        return style;
      }
    };
  }
}
