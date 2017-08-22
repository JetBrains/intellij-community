/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.codeStyleSettings;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jdom.Element;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author Rustam Vishnyakov
 */
public class CodeStyleConfigurationTest extends CodeStyleTestCase {

  /**
   * Check that indent options are correcly read if mixed with other language options
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
      "      <option name=\"CONTINUATION_INDENT_SIZE\" value=\"8\" />\n" +
      "      <option name=\"TAB_SIZE\" value=\"4\" />\n" +
      "      <option name=\"USE_TAB_CHARACTER\" value=\"false\" />\n" +
      "      <option name=\"SMART_TABS\" value=\"false\" />\n" +
      "      <option name=\"LABEL_INDENT_SIZE\" value=\"0\" />\n" +
      "      <option name=\"LABEL_INDENT_ABSOLUTE\" value=\"false\" />\n" +
      "      <option name=\"USE_RELATIVE_INDENTS\" value=\"false\" />\n" +
      "    </value>\n" +
      "  </option>\n" +
      "</option>",
      root);
  }


}
