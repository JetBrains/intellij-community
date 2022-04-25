// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.execution;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParametersListUtilTest {

  private void testInvariants(String arg) {
    var escaped = ParametersListUtil.escape(arg);
    var parsed = ParametersListUtil.parse(escaped);
    assertThat(parsed).hasSize(1);
    assertThat(parsed.get(0)).isEqualTo(arg);
  }

  @Test
  public void testNoSpaceWhiteSpaceInParameterList() {
    testInvariants("-Xplugin:ErrorProne\n" +
                   "-XepDisableWarningsInGeneratedCode"
    );

    testInvariants("-Xplugin:ErrorProne\t" +
                   "-XepDisableWarningsInGeneratedCode"
    );
  }
}