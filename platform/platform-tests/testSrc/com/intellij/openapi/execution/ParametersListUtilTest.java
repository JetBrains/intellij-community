// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.execution;

import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class ParametersListUtilTest {

  private static void checkInvariants(@NotNull String arg) {
    String escaped = ParametersListUtil.escape(arg);
    List<String> parsed = ParametersListUtil.parse(escaped);
    assertThat(parsed).hasSize(1);
    assertThat(parsed.get(0)).isEqualTo(arg);
  }

  @Test
  public void testNoSpaceWhiteSpaceInParameterList() {
    checkInvariants("-Xplugin:ErrorProne\n" +
                    "-XepDisableWarningsInGeneratedCode");

    checkInvariants("-Xplugin:ErrorProne\t" +
                    "-XepDisableWarningsInGeneratedCode");
  }

  @Test
  public void testQuotesInParameterList() {
    final List<String> expected = asList(
      "cmd",
      "-a",
      "-b",
      "arg0",
      "-c",
      "--long-option",
      "--long-opt2=arg1",
      "arg2",
      "arg3",
      "-a",
      "a \"r g",
      "--foo=d e f"
    );
    final String doubleQuotes = "cmd -a -b arg0 -c --long-option    --long-opt2=arg1 arg2 arg3 -a \"a \\\"r g\" --foo=\"d e f\"\"\"";
    assertEquals("Double quotes broken", expected, ParametersListUtil.parse(doubleQuotes, false, true));

    final String singleQuotes = "cmd -a -b arg0 -c --long-option    --long-opt2=arg1 arg2 arg3 -a 'a \"r g' --foo='d e f'";
    assertEquals("Single quotes broken", expected, ParametersListUtil.parse(singleQuotes, false, true));

    final String mixedQuotes = "cmd -a -b arg0 -c --long-option    --long-opt2=arg1 arg2 arg3 -a \"a \\\"r g\" --foo='d e f'";
    assertEquals("Mixed quotes broken", expected, ParametersListUtil.parse(mixedQuotes, false, true));
  }

  @Test
  public void testEmptyParameters() {
    assertEquals("Expected empty parameters", asList("cmd", "", "-a", "", "", "text", "--long-option", ""),
                 ParametersListUtil.parse("cmd \"\" -a \"\" '' 'text' --long-option \"\"", false, true));

    assertEquals("Should be no empty parameters", asList("cmd", "-a", "text", "--long-option"),
                 ParametersListUtil.parse("cmd  -a   'text' --long-option ", false, true));

    assertEquals("Empty trailing parameter broken", asList("cmd", "", "-a", "", "", "text", "--long-option", ""),
                 ParametersListUtil.parse("cmd  -a   'text' --long-option ", false, true, true));
  }
}