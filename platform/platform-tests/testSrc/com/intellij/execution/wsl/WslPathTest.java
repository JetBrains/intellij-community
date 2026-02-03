// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class WslPathTest {
  @BeforeClass
  public static void compatibilityCheck() {
    assumeTrue(WSLUtil.isSystemCompatible());
  }

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> prefixes() {
    return List.of(new Object[]{"\\\\wsl$\\"}, new Object[]{"\\\\wsl.localhost\\"});
  }

  @Parameterized.Parameter
  public String prefix;

  @Test
  public void parsing() {
    checkParse("", null);
    checkParse("/mnt/c/usr/bin", null);
    checkParse("C:\\Users\\user", null);

    checkParse(prefix.substring(0, prefix.length() - 1), null);
    checkParse(prefix, null);
    checkParse(prefix + "Ubuntu", null);
    checkParse(prefix + "\\etc", null);

    checkParse(prefix + "Ubuntu\\", new WslPath(prefix, "Ubuntu", "/"));
    checkParse(prefix + "Ubuntu\\etc", new WslPath(prefix, "Ubuntu", "/etc"));
    checkParse(prefix + "Ubuntu\\etc\\hosts", new WslPath(prefix, "Ubuntu", "/etc/hosts"));
    checkParse(prefix.replace('\\', '/') + "Ubuntu/etc", new WslPath(prefix, "Ubuntu", "/etc"));
    checkParse(prefix.replace('\\', '/') + "Ubuntu/etc/hosts", new WslPath(prefix, "Ubuntu", "/etc/hosts"));
  }

  private static void checkParse(@NotNull String windowsPath, @Nullable WslPath expectedWslPath) {
    WslPath actualWslPath = WslPath.parseWindowsUncPath(windowsPath);
    assertEquals(expectedWslPath, actualWslPath);
    if (actualWslPath != null) {
      assertEquals(FileUtil.toSystemDependentName(windowsPath), actualWslPath.toWindowsUncPath());
    }
  }
}
