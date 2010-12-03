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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 12/02/2010
 */
public class FoldingDataProviderTest {

  private FoldingDataProvider myProvider;  
    
  @Before
  public void setUp() {
    myProvider = new FoldingDataProvider(new FoldRegion[] {new MockFoldRegion(1, 3), new MockFoldRegion(5, 10), new MockFoldRegion(15, 20)});
  }
  
  @Test
  public void advanceToExactRegionStartOffset() {
    myProvider.advance(5);
    assertEquals(5, myProvider.getSortingKey());
  }
}
