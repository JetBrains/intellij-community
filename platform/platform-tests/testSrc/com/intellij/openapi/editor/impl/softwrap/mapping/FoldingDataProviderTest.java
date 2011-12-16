/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.mock.MockFoldRegion;
import com.intellij.openapi.editor.FoldRegion;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Denis Zhdanov
 * @since 12/02/2010
 */
public class FoldingDataProviderTest {

  FoldingDataProvider myProvider;
  
  @Test
  public void advanceToExactRegionStartOffset() {
    init(new MockFoldRegion(1, 3), new MockFoldRegion(5, 10), new MockFoldRegion(15, 20));
    myProvider.advance(5);
    assertEquals(5, myProvider.getSortingKey());
  }
  
  @Test
  public void invalidRegionsAreIgnored() {
    init(new MockFoldRegion(1, 3), new MockFoldRegion(5, 6, false), new MockFoldRegion(7, 8), new MockFoldRegion(10, 11, false));
    assertEquals(1, myProvider.getSortingKey());

    assertTrue(myProvider.next());
    assertEquals(7, myProvider.getSortingKey());

    assertFalse(myProvider.next());
    assertNull(myProvider.getData());
  }

  @Test
  public void invalidRegionsAreIgnoredDuringAdvancing() {
    init(new MockFoldRegion(1, 3), new MockFoldRegion(5, 6, false), new MockFoldRegion(7, 8), new MockFoldRegion(10, 11, false));
    assertEquals(1, myProvider.getSortingKey());
    
    myProvider.advance(5);
    assertEquals(7, myProvider.getSortingKey());

    assertFalse(myProvider.next());
    assertNull(myProvider.getData());
  }
  
  private void init(FoldRegion ... regions) {
    myProvider = new FoldingDataProvider(regions);
  }
}
