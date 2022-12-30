// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.codeStyleSettings;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.excludedFiles.GlobPatternDescriptor;
import com.intellij.formatting.fileSet.FileSetDescriptor;
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

    CodeStyleSettings settings = CodeStyle.createTestSettings();
    settings.readExternal(rootElement);
    CommonCodeStyleSettings langSettings = settings.getCommonSettings(XMLLanguage.INSTANCE);
    assert langSettings != null;
    CommonCodeStyleSettings.IndentOptions indentOptions = langSettings.getIndentOptions();
    assert indentOptions != null;
    assertEquals(2, indentOptions.INDENT_SIZE);
    assertEquals(3, indentOptions.CONTINUATION_INDENT_SIZE);
    assertEquals(2, indentOptions.TAB_SIZE);
    assertTrue(indentOptions.USE_TAB_CHARACTER);
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
    CodeStyleSettings settings = CodeStyle.createTestSettings();
    settings.OTHER_INDENT_OPTIONS.INDENT_SIZE = 2;
    Element root = createOption("config", "root");
    settings.writeExternal(root);
    root.removeAttribute("version");
    assertXmlOutputEquals(
      """
        <option name="config" value="root">
          <option name="OTHER_INDENT_OPTIONS">
            <value>
              <option name="INDENT_SIZE" value="2" />
            </value>
          </option>
        </option>""",
      root);
  }

  public void testSaveSoftMargins() throws Exception {
    CodeStyleSettings settings = CodeStyle.createTestSettings();
    settings.setDefaultRightMargin(110);
    settings.setDefaultSoftMargins(Arrays.asList(60, 80, 140));
    Element root = createOption("config", "root");
    settings.writeExternal(root);
    root.removeAttribute("version");
    assertXmlOutputEquals(
      """
        <option name="config" value="root">
          <option name="RIGHT_MARGIN" value="110" />
          <option name="SOFT_MARGINS" value="60,80,140" />
        </option>""",
      root);
  }

  public void testReadSoftMargins() throws Exception {
    CodeStyleSettings settings = CodeStyle.createTestSettings();
    String source =
      """
        <option name="config" value="root">
          <option name="RIGHT_MARGIN" value="110" />
          <option name="SOFT_MARGINS" value="60,80,140" />
        </option>""";
    Element root = JDOMUtil.load(source);
    settings.readExternal(root);
    assertEquals(110, settings.getDefaultRightMargin());
    List<Integer> softMargins = settings.getDefaultSoftMargins();
    assertEquals(3, softMargins.size());
    assertEquals(60, softMargins.get(0).intValue());
    assertEquals(80, softMargins.get(1).intValue());
    assertEquals(140, softMargins.get(2).intValue());
  }

  public void testSaveExcludedFiles() throws Exception {
    CodeStyleSettings settings = CodeStyle.createTestSettings();
    settings.getExcludedFiles().addDescriptor(new GlobPatternDescriptor("*.java"));
    settings.getExcludedFiles().addDescriptor(new GlobPatternDescriptor("/lib/**/*.min.js"));
    Element root = createOption("config", "root");
    settings.writeExternal(root);
    root.removeAttribute("version");
    assertXmlOutputEquals(
      """
        <option name="config" value="root">
          <option name="DO_NOT_FORMAT">
            <list>
              <fileSet type="globPattern" pattern="*.java" />
              <fileSet type="globPattern" pattern="/lib/**/*.min.js" />
            </list>
          </option>
        </option>""",
      root);
  }

  public void testReadExcludedFiles() throws Exception {
    CodeStyleSettings settings = CodeStyle.createTestSettings();
    String source =
      """
        <option name="config" value="root">
          <option name="DO_NOT_FORMAT">
            <list>
              <fileSet type="globPattern" pattern="*.java" />
              <fileSet type="globPattern" pattern="/lib/**/*.min.js" />
            </list>
          </option>
        </option>""";
    Element root = JDOMUtil.load(source);
    settings.readExternal(root);
    List<FileSetDescriptor> descriptors = settings.getExcludedFiles().getDescriptors();
    assertSize(2, descriptors);
    assertEquals("*.java", descriptors.get(0).getPattern());
    assertEquals("/lib/**/*.min.js", descriptors.get(1).getPattern());
  }
}
