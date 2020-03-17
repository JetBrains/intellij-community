// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests;

import git4idea.GitUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GitUtilsTest {

  @Test
  public void format_long_rev() {
    assertEquals("0000000000000000", GitUtil.formatLongRev(0));
    assertEquals("fffffffffffffffe", GitUtil.formatLongRev(-2));
  }
}
