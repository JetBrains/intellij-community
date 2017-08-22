/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class TokenSetTest {
  @Parameterized.Parameters
  public static List<Object[]> data() {
    return Collections.nCopies(10, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  private static IElementType T1, T2, T3, T4, T5, T6;
  private TokenSet S1, S12, S3, S34, S5;

  @BeforeClass
  public static void setUp() {
    T1 = new IElementType("T1", Language.ANY);
    T2 = new IElementType("T2", Language.ANY);
    fakeElements(1, 128);
    T3 = new IElementType("T3", Language.ANY);
    T4 = new IElementType("T4", Language.ANY);
    fakeElements(201, 204);
    T5 = new IElementType("T5", Language.ANY);
    T6 = new IElementType("T6", Language.ANY);
  }

  @Before
  public void isetup() {
    S1 = TokenSet.create(T1);
    S12 = TokenSet.create(T1, T2);
    S3 = TokenSet.create(T3);
    S34 = TokenSet.create(T3, T4);
    S5 = TokenSet.create(T5);
  }

  @Test
  public void create() {
    check(S1, T1);
    check(S12, T1, T2);
    check(S3, T3);
    check(S34, T3, T4);
  }

  @Test
  public void getTypes() {
    assertArrayEquals(IElementType.EMPTY_ARRAY, TokenSet.EMPTY.getTypes());
    assertArrayEquals(new IElementType[]{T1, T2}, S12.getTypes());
    assertArrayEquals(new IElementType[]{T3, T4}, S34.getTypes());
    assertEquals("[]", TokenSet.EMPTY.toString());
    assertEquals("[T1, T2]", S12.toString());
    assertEquals("[T3, T4]", S34.toString());
  }

  @Test
  public void orSet() {
    check(TokenSet.orSet(S1, S12, S3), T1, T2, T3);
    check(TokenSet.orSet(S1, S3), T1, T3);
  }

  @Test
  public void andSet() {
    check(TokenSet.andSet(S1, S12), T1);
    check(TokenSet.andSet(S12, S34));
  }

  @Test
  public void andNot() {
    final TokenSet S123 = TokenSet.orSet(S12, S3);
    check(TokenSet.andNot(S123, S12), T3);
    check(TokenSet.andNot(S123, S5), T1, T2, T3);
  }

  private static void fakeElements(int from, int to) {
    for (int i = from; i <= to; i++) {
      new IElementType("Test element #" + i, Language.ANY);
    }
  }

  private static void check(@NotNull TokenSet set, @NotNull IElementType... elements) {
    final Set<IElementType> expected = ContainerUtil.newHashSet(elements);
    for (IElementType t : Arrays.asList(T1, T2, T3, T4, T5, T6)) {
      if (expected.contains(t)) {
        assertTrue("missed: " + t, set.contains(t));
      }
      else {
        assertFalse("unexpected: " + t, set.contains(t));
      }
    }
  }


  @Test
  public void performance() {
    final IElementType[] elementTypes = IElementType.enumerate(IElementType.TRUE);
    final TokenSet set = TokenSet.create();
    final int shift = new Random().nextInt(500000);

    PlatformTestUtil.startPerformanceTest("TokenSet.contains()", 25, () -> {
      for (int i = 0; i < 1000000; i++) {
        final IElementType next = elementTypes[(i + shift) % elementTypes.length];
        assertFalse(set.contains(next));
      }
    }).useLegacyScaling().assertTiming();
  }
}
