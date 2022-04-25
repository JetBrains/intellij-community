// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.execution;

import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}