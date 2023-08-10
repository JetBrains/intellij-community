// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.util.ExecUtil;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceIntegrityTest {
  @Test
  public void desktopEntry() throws Exception {
    var contents = ExecUtil.loadTemplate(CreateDesktopEntryAction.class.getClassLoader(), "entry.desktop", Collections.emptyMap());
    assertThat(contents).doesNotContain("\r\n");
  }
}
