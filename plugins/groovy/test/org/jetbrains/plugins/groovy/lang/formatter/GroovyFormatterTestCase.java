// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class GroovyFormatterTestCase extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setSettings(getProject());

    getGroovySettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    getGroovySettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    getGroovySettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
  }

  protected CommonCodeStyleSettings getGroovySettings() {
    return myTempSettings.getCommonSettings(GroovyLanguage.INSTANCE);
  }

  protected GroovyCodeStyleSettings getGroovyCustomSettings() {
    return myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class);
  }

  protected void setSettings(Project project) {
    TestCase.assertNull(myTempSettings);
    CodeStyleSettings settings = CodeStyle.getSettings(project);
    myTempSettings = settings;

    CommonCodeStyleSettings.IndentOptions gr = myTempSettings.getIndentOptions(GroovyFileType.GROOVY_FILE_TYPE);
    TestCase.assertNotSame(gr, settings.OTHER_INDENT_OPTIONS);
    gr.INDENT_SIZE = 2;
    gr.CONTINUATION_INDENT_SIZE = 4;
    gr.TAB_SIZE = 2;
    myTempSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
  }

  protected void checkFormatting(String fileText, String expected) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);
    checkFormatting(expected);
  }

  protected void doFormat(final PsiFile file) {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        TextRange myTextRange = file.getTextRange();
        CodeStyleManager.getInstance(file.getProject()).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), null, null);
  }

  protected void checkFormatting(String expected) {
    doFormat(myFixture.getFile());
    myFixture.checkResult(expected);
  }

  public void doTest(String fileName) throws Throwable {
    final List<String> data = TestUtils.readInput(getTestDataPath() + fileName);
    String inputWithOptions = data.get(0);
    String input = inputWithOptions;
    while (true) {
      final Iterator<Serializable> iterator = parseOption(input).iterator();
      String name = ((String)(iterator.hasNext() ? iterator.next() : null));
      String value = ((String)(iterator.hasNext() ? iterator.next() : null));
      Integer matcherEnd = ((Integer)(iterator.hasNext() ? iterator.next() : null));

      if (!StringGroovyMethods.asBoolean(name) || !StringGroovyMethods.asBoolean(value)) break;

      final Iterator<Object> iterator1 = findSettings(name).iterator();
      Field field = ((Field)(iterator1.hasNext() ? iterator1.next() : null));
      Object settingObj = iterator1.hasNext() ? iterator1.next() : null;

      field.set(settingObj, evaluateValue(value));
      input = input.substring(matcherEnd);
    }


    checkFormattingByFile(input, inputWithOptions, fileName);
  }

  protected void checkFormattingByFile(String input, String inputWithOptions, String path) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, input);
    doFormat(myFixture.getFile());
    final String prefix = inputWithOptions + "\n-----\n";
    myFixture.configureByText("test.txt", prefix + myFixture.getFile().getText());
    myFixture.checkResultByFile(path, false);
  }

  @NotNull
  public static List<Serializable> parseOption(String input) {
    final Matcher matcher = PATTERN.matcher(input);
    if (!matcher.find()) return Arrays.asList(null, null, null);
    final String[] strings = matcher.group(1).split("=");
    return new ArrayList<>(Arrays.asList(strings[0], strings[1], matcher.end()));
  }

  private List<Object> findSettings(String name) {
    Field commonCodeStyleSettings = findField(CommonCodeStyleSettings.class, name);
    if (commonCodeStyleSettings != null) {
      return Arrays.asList(commonCodeStyleSettings, getGroovySettings());
    }
    Field groovyCodeStyleSettings = findField(GroovyCodeStyleSettings.class, name);
    if (groovyCodeStyleSettings != null) {
      return Arrays.asList(groovyCodeStyleSettings, getGroovyCustomSettings());
    }
    Field indentOptions = findField(CommonCodeStyleSettings.IndentOptions.class, name);
    return indentOptions != null ? Arrays.asList(indentOptions, getGroovySettings().getIndentOptions()) : null;
  }

  private static @Nullable Field findField(Class<?> clazz, final String name) {
    return ContainerUtil.find(clazz.getFields(), f -> f.getName().equals(name));
  }

  @Nullable
  private static Object evaluateValue(String value) {
    if (value.equals("true") || value.equals("false")) {
      return Boolean.parseBoolean(value);
    }
    else {
      try {
        return Integer.parseInt(value);
      }
      catch (NumberFormatException ignored) {
        try {
          return CommonCodeStyleSettings.class.getField(value).get(value);
        }
        catch (IllegalAccessException | NoSuchFieldException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private static final Logger LOG = Logger.getInstance(GroovyFormatterTestCase.class);
  private static final String OPTION_START = "<option>";
  private static final String OPTION_END = "</option>";
  private static final Pattern PATTERN =
    StringGroovyMethods.bitwiseNegate(OPTION_START + "(\\w+=(true|false+|\\d|\\w+))" + OPTION_END + "\n");
  protected CodeStyleSettings myTempSettings;
}
