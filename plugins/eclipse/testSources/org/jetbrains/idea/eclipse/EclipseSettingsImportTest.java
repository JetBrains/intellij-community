package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.idea.eclipse.importer.EclipseCodeStyleSchemeImporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * @author Rustam Vishnyakov
 */
public class EclipseSettingsImportTest extends PlatformTestCase {
  
  private static String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("eclipse") + "/testData/import/settings/";
  }
  
  public void testImportCodeStyleSettingsFromXmlProfile() throws Exception {
    File input = new File(getTestDataPath() + "eclipse_exported.xml");
    EclipseCodeStyleSchemeImporter codeStyleSchemeImporter = new EclipseCodeStyleSchemeImporter();
    CodeStyleSchemes schemes = CodeStyleSchemes.getInstance();
    CodeStyleScheme scheme = schemes.createNewScheme(getTestName(false), null);
    CodeStyleSettings settings = scheme.getCodeStyleSettings();
    
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings("Java");
    CommonCodeStyleSettings.IndentOptions indentOptions = javaSettings.getIndentOptions();
    javaSettings.SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = false;
    javaSettings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = false;
    javaSettings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = true;
    javaSettings.SPACE_WITHIN_ANNOTATION_PARENTHESES = true;
    javaSettings.BLANK_LINES_AROUND_FIELD = -1;
    javaSettings.SPACE_WITHIN_WHILE_PARENTHESES = true;
    javaSettings.ELSE_ON_NEW_LINE = true;
    javaSettings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
    javaSettings.SPACE_BEFORE_FOR_PARENTHESES = false;
    javaSettings.SPACE_AROUND_ADDITIVE_OPERATORS = false;
    javaSettings.SPACE_AROUND_BITWISE_OPERATORS = false;
    javaSettings.SPACE_AROUND_EQUALITY_OPERATORS = false;
    javaSettings.SPACE_AROUND_LOGICAL_OPERATORS = false;
    javaSettings.FINALLY_ON_NEW_LINE = true;
    javaSettings.CATCH_ON_NEW_LINE = true;
    javaSettings.SPACE_BEFORE_WHILE_PARENTHESES = false;
    javaSettings.BLANK_LINES_AFTER_PACKAGE = -1;
    javaSettings.getIndentOptions().CONTINUATION_INDENT_SIZE = 0;
    javaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    javaSettings.BLANK_LINES_BEFORE_PACKAGE = -1;
    javaSettings.SPACE_WITHIN_FOR_PARENTHESES = true;
    javaSettings.ALIGN_MULTILINE_ASSIGNMENT = true;
    javaSettings.SPACE_BEFORE_METHOD_PARENTHESES = true;
    javaSettings.SPACE_WITHIN_CATCH_PARENTHESES = true;
    javaSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
    javaSettings.SPACE_WITHIN_CAST_PARENTHESES = true;
    javaSettings.SPACE_AROUND_UNARY_OPERATOR = true;
    javaSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    boolean currAddLineFeed = editorSettings.isEnsureNewLineAtEOF();
    editorSettings.setEnsureNewLineAtEOF(true);
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    javaSettings.SPACE_WITHIN_TRY_PARENTHESES = true;
    javaSettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true;
    javaSettings.WHILE_ON_NEW_LINE = true;
    settings.ENABLE_JAVADOC_FORMATTING = false;
    javaSettings.SPACE_BEFORE_SEMICOLON = true;
    javaSettings.BLANK_LINES_BEFORE_METHOD_BODY = -1;
    javaSettings.SPACE_BEFORE_COLON = false;
    javaSettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = true;
    javaSettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;
    javaSettings.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = true;
    javaSettings.SPACE_BEFORE_QUEST = false;
    javaSettings.BLANK_LINES_BEFORE_IMPORTS = 0;
    javaSettings.ALIGN_MULTILINE_THROWS_LIST = true;
    javaSettings.SPACE_AFTER_COLON = false;
    javaSettings.SPACE_WITHIN_FOR_PARENTHESES = true;
    javaSettings.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = false;
    javaSettings.ALIGN_MULTILINE_PARAMETERS = true;
    javaSettings.ALIGN_MULTILINE_RESOURCES = true;
    javaSettings.SPACE_BEFORE_SWITCH_PARENTHESES = false;
    javaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    javaSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    javaSettings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false;
    javaSettings.SPACE_WITHIN_METHOD_PARENTHESES = true;
    javaSettings.SPACE_BEFORE_CATCH_PARENTHESES = false;
    javaSettings.SPACE_WITHIN_ANNOTATION_PARENTHESES = true;
    javaSettings.BLANK_LINES_AFTER_IMPORTS = -1;
    javaSettings.KEEP_FIRST_COLUMN_COMMENT = true;
    javaSettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;
    indentOptions.USE_TAB_CHARACTER = false;
    indentOptions.SMART_TABS = false;
    settings.FORMATTER_TAGS_ENABLED = false;

    InputStream inputStream = new FileInputStream(input);
    try {
      codeStyleSchemeImporter.importScheme(inputStream, null, scheme);

      assertTrue(javaSettings.SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS);
      assertTrue(javaSettings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES);
      assertFalse(javaSettings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE);
      assertFalse(javaSettings.SPACE_WITHIN_ANNOTATION_PARENTHESES);
      assertEquals(0, javaSettings.BLANK_LINES_AROUND_FIELD);
      assertFalse(javaSettings.SPACE_WITHIN_WHILE_PARENTHESES);
      assertFalse(javaSettings.ELSE_ON_NEW_LINE);
      assertFalse(javaSettings.ALIGN_GROUP_FIELD_DECLARATIONS);
      assertTrue(javaSettings.SPACE_BEFORE_FOR_PARENTHESES);
      assertTrue(javaSettings.SPACE_AROUND_ADDITIVE_OPERATORS);
      assertTrue(javaSettings.SPACE_AROUND_BITWISE_OPERATORS);
      assertTrue(javaSettings.SPACE_AROUND_EQUALITY_OPERATORS);
      assertTrue(javaSettings.SPACE_AROUND_LOGICAL_OPERATORS);
      assertFalse(javaSettings.FINALLY_ON_NEW_LINE);
      assertFalse(javaSettings.CATCH_ON_NEW_LINE);
      assertTrue(javaSettings.SPACE_BEFORE_WHILE_PARENTHESES);
      assertEquals(1, javaSettings.BLANK_LINES_AFTER_PACKAGE);
      assertEquals(2, javaSettings.getIndentOptions().CONTINUATION_INDENT_SIZE);
      assertFalse(javaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
      assertEquals(0, javaSettings.BLANK_LINES_BEFORE_PACKAGE);
      assertFalse(javaSettings.SPACE_WITHIN_FOR_PARENTHESES);
      assertFalse(javaSettings.ALIGN_MULTILINE_ASSIGNMENT);
      assertFalse(javaSettings.SPACE_BEFORE_METHOD_PARENTHESES);
      assertFalse(javaSettings.SPACE_WITHIN_CATCH_PARENTHESES);
      assertFalse(javaSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
      assertFalse(javaSettings.SPACE_WITHIN_CATCH_PARENTHESES);
      assertFalse(javaSettings.SPACE_AROUND_UNARY_OPERATOR);
      assertTrue(javaSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
      assertFalse(editorSettings.isEnsureNewLineAtEOF());
      assertFalse(javaSettings.ALIGN_MULTILINE_BINARY_OPERATION);
      assertFalse(javaSettings.SPACE_WITHIN_TRY_PARENTHESES);
      assertFalse(javaSettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES);
      assertFalse(javaSettings.WHILE_ON_NEW_LINE);
      assertTrue(settings.ENABLE_JAVADOC_FORMATTING);
      assertFalse(javaSettings.SPACE_BEFORE_SEMICOLON);
      assertEquals(0, javaSettings.BLANK_LINES_BEFORE_METHOD_BODY);
      assertTrue(javaSettings.SPACE_BEFORE_COLON);
      assertFalse(javaSettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS);
      assertTrue(javaSettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE);
      assertFalse(javaSettings.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES);
      assertTrue(javaSettings.SPACE_BEFORE_QUEST);
      assertEquals(1, javaSettings.BLANK_LINES_BEFORE_IMPORTS);
      assertFalse(javaSettings.ALIGN_MULTILINE_THROWS_LIST);
      assertTrue(javaSettings.SPACE_AFTER_COLON);
      assertFalse(javaSettings.SPACE_WITHIN_FOR_PARENTHESES);
      assertTrue(javaSettings.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES);
      assertFalse(javaSettings.ALIGN_MULTILINE_PARAMETERS);
      assertFalse(javaSettings.ALIGN_MULTILINE_RESOURCES);
      assertTrue(javaSettings.SPACE_BEFORE_SWITCH_PARENTHESES);
      assertFalse(javaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES);
      assertEquals(CommonCodeStyleSettings.END_OF_LINE, javaSettings.CLASS_BRACE_STYLE);
      assertTrue(javaSettings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE);
      assertFalse(javaSettings.SPACE_WITHIN_METHOD_PARENTHESES);
      assertTrue(javaSettings.SPACE_BEFORE_CATCH_PARENTHESES);
      assertFalse(javaSettings.SPACE_WITHIN_ANNOTATION_PARENTHESES);
      assertEquals(1, javaSettings.BLANK_LINES_AFTER_IMPORTS);
      assertFalse(javaSettings.KEEP_FIRST_COLUMN_COMMENT);
      assertFalse(javaSettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE);
      assertTrue(indentOptions.USE_TAB_CHARACTER);
      assertTrue(indentOptions.SMART_TABS);
      assertTrue(settings.FORMATTER_TAGS_ENABLED);
      assertEquals("@off_tag", settings.FORMATTER_OFF_TAG);
      assertEquals("@on_tag", settings.FORMATTER_ON_TAG);
    }
    finally {
      inputStream.close();
      schemes.deleteScheme(scheme);
      editorSettings.setEnsureNewLineAtEOF(currAddLineFeed);
    }
  }
}
