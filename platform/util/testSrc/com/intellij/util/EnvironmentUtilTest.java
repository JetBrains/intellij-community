/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author mike
 * @since Sep 19, 2002
 */
public class EnvironmentUtilTest {
  @Test(timeout = 60000)
  public void map() throws Exception {
    System.setProperty("idea.fix.mac.env", "true");
    assertNotNull(EnvironmentUtil.getEnvironmentMap());
  }

  @Test
  public void path() {
    assertNotNull(EnvironmentUtil.getValue("PATH"));
    if (SystemInfo.isWindows) {
      assertNotNull(EnvironmentUtil.getValue("Path"));
    }
  }
}
