// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.codeStyleSettings;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jdom.Element;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
public class CodeStyleConfigurationTest extends CodeStyleTestCase {
  /**
   * Check that indent options are correctly read if mixed with other language options
   */
  public void testIndentOptionsRead() {
    org.jdom.Element rootElement = new Element("option");

    Element langCodeStyle = new Element("codeStyleSettings");
    langCodeStyle.setAttribute("language", "XML");
    langCodeStyle.addContent(createOption("IF_BRACE_FORCE", "3"));
    Element indentOptionsElement = new Element("indentOptions");
    indentOptionsElement.addContent(createOption("INDENT_SIZE", "2"));
    indentOptionsElement.addContent(createOption("CONTINUATION_INDENT_SIZE", "3"));
    indentOptionsElement.addContent(createOption("TAB_SIZE", "2"));
    indentOptionsElement.addContent(createOption("USE_TAB_CHARACTER", "true"));
    langCodeStyle.addContent(indentOptionsElement);
    rootElement.addContent(langCodeStyle);

    CodeStyleSettings settings = new CodeStyleSettings();
    settings.readExternal(rootElement);
    CommonCodeStyleSettings langSettings = settings.getCommonSettings(XMLLanguage.INSTANCE);
    assert langSettings != null;
    CommonCodeStyleSettings.IndentOptions indentOptions = langSettings.getIndentOptions();
    assert indentOptions != null;
    assertEquals(2, indentOptions.INDENT_SIZE);
    assertEquals(3, indentOptions.CONTINUATION_INDENT_SIZE);
    assertEquals(2, indentOptions.TAB_SIZE);
    assertEquals(true, indentOptions.USE_TAB_CHARACTER);
    assertEquals(3, langSettings.IF_BRACE_FORCE);
  }

  public void testCodeStyleSettingsCustomizableOptions() {
    Field[] commonFields = CommonCodeStyleSettings.class.getFields();    
    for (Field field : commonFields) {      
      boolean fieldExistsInCustomizable = false;
      String fieldName = field.getName();
      int fieldModifiers = field.getModifiers();
      if (Modifier.isPublic(fieldModifiers) && 
          !Modifier.isStatic(fieldModifiers) &&
          !"PARENT_SETTINGS_INSTALLED".equals(fieldName) &&
          !"FORCE_REARRANGE_MODE".equals(fieldName)
        ) {
        for (CodeStyleSettingsCustomizable.SpacingOption option : CodeStyleSettingsCustomizable.SpacingOption.values()) {
          if (option.toString().equals(field.getName())) {
            fieldExistsInCustomizable = true;
            break;
          }
        }
        if (!fieldExistsInCustomizable) {
          for (CodeStyleSettingsCustomizable.WrappingOrBraceOption option : CodeStyleSettingsCustomizable.WrappingOrBraceOption.values()) {
            if (option.toString().equals(field.getName())) {
              fieldExistsInCustomizable = true;
              break;
            }
          }
        }
        if (!fieldExistsInCustomizable) {
          for (CodeStyleSettingsCustomizable.BlankLinesOption option : CodeStyleSettingsCustomizable.BlankLinesOption.values()) {
            if (option.toString().equals(field.getName())) {
              fieldExistsInCustomizable = true;
              break;
            }
          }
        }
        if (!fieldExistsInCustomizable) {
          for (CodeStyleSettingsCustomizable.CommenterOption option : CodeStyleSettingsCustomizable.CommenterOption.values()) {
            if (option.toString().equals(field.getName())) {
              fieldExistsInCustomizable = true;
              break;
            }
          }
        }
        assertTrue("Field " + field.getName() + " is not declared in CodeStyleSettingsCustomizable", fieldExistsInCustomizable);
      }
    }
  }

  public void testSaveOtherOptionsChanged() throws Exception {
    CodeStyleSettings settings = new CodeStyleSettings();
    settings.OTHER_INDENT_OPTIONS.INDENT_SIZE = 2;
    Element root = createOption("config", "root");
    settings.writeExternal(root);
    root.removeAttribute("version");
    assertXmlOutputEquals(
      "<option name=\"config\" value=\"root\">\n" +
      "  <option name=\"OTHER_INDENT_OPTIONS\">\n" +
      "    <value>\n" +
      "      <option name=\"INDENT_SIZE\" value=\"2\" />\n" +
      "    </value>\n" +
      "  </option>\n" +
      "</option>",
      root);
  }

  public void testSaveSoftMargins() throws Exception {
    CodeStyleSettings settings = new CodeStyleSettings();
    settings.setDefaultRightMargin(110);
    settings.setDefaultSoftMargins(Arrays.asList(60, 80, 140));
    Element root = createOption("config", "root");
    settings.writeExternal(root);
    root.removeAttribute("version");
    assertXmlOutputEquals(
      "<option name=\"config\" value=\"root\">\n" +
      "  <option name=\"RIGHT_MARGIN\" value=\"110\" />\n" +
      "  <option name=\"SOFT_MARGINS\" value=\"60,80,140\" />\n" +
      "</option>",
      root);
  }

  public void testReadSoftMargins() throws Exception {
    CodeStyleSettings settings = new CodeStyleSettings();
    String source =
      "<option name=\"config\" value=\"root\">\n" +
      "  <option name=\"RIGHT_MARGIN\" value=\"110\" />\n" +
      "  <option name=\"SOFT_MARGINS\" value=\"60,80,140\" />\n" +
      "</option>";
    Element root = JDOMUtil.load(source);
    settings.readExternal(root);
    assertEquals(110, settings.getDefaultRightMargin());
    List<Integer> softMargins = settings.getDefaultSoftMargins();
    assertEquals(3, softMargins.size());
    assertEquals(60, softMargins.get(0).intValue());
    assertEquals(80, softMargins.get(1).intValue());
    assertEquals(140, softMargins.get(2).intValue());
  }
}
