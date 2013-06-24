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
package com.intellij.util.text;

import junit.framework.Assert;
import org.junit.Test;

/**
 * @author Sergey Simonchik
 */
public class SemVerTest {
  @Test
  public void testParsing() throws Exception {
    SemVer semVer = SemVer.parseFromText("0.9.2");
    Assert.assertNotNull(semVer);
    Assert.assertEquals(new SemVer(0, 9, 2), semVer);
  }

  @Test
  public void testExtendedVersion() throws Exception {
    SemVer semVer = SemVer.parseFromText("0.9.2-dart");
    Assert.assertNotNull(semVer);
    Assert.assertEquals(new SemVer(0, 9, 2), semVer);
  }
}
