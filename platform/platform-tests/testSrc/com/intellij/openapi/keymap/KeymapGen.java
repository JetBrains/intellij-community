// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap;

import com.intellij.idea.IgnoreJUnit3;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;

import java.io.File;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("JUnitTestClassNamingConvention")
public class KeymapGen extends LightPlatformTestCase {
  @IgnoreJUnit3
  public void testGenerate() throws Exception {
    StringBuilder xml = new StringBuilder();
    xml.append("<Keymaps>\n");

    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      xml.append("  <Keymap name=\"").append(keymap.getName()).append("\">\n");
      for (String id : keymap.getActionIdList()) {
        String shortcuts = KeymapUtil.getShortcutsText(keymap.getShortcuts(id));
        if (!StringUtil.isEmpty(shortcuts)) {
          xml.append("    <Action id=\"").append(id).append("\">\n");
          for (Shortcut shortcut : keymap.getShortcuts(id)) {
            xml.append("      <Shortcut>").append(KeymapUtil.getShortcutText(shortcut)).append("</Shortcut>\n");
          }
          xml.append("    </Action>\n");
        }
      }
      xml.append("  </Keymap>\n");
    }
    xml.append("</Keymaps>");

    File out = new File(PathManager.getHomePath() + File.separator + "AllKeymaps.xml");
    FileUtil.writeToFile(out, xml.toString());
    System.out.println("Saved to: " + out.getAbsolutePath());
  }
}
