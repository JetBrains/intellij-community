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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Denis Zhdanov
 * @since 11/15/10 11:08 AM
 */
public class DelayedRemovalMapTest {
  
  private DelayedRemovalMap<Integer> myMap;
  
  @Before
  public void setUp() {
    myMap = new DelayedRemovalMap<Integer>();
  }
  
  @Test
  public void delayedRemoval() {
    myMap.put(1, 1);
    myMap.put(2, 1);
    myMap.put(3, 1);
    myMap.put(4, 1);
    myMap.markForDeletion(2, 3);
    myMap.deleteMarked();
    
    assertEquals(2, myMap.size());
    assertNotNull(myMap.get(1));
    assertNull(myMap.get(2));
    assertNull(myMap.get(3));
    assertNotNull(myMap.get(4));
  }
  
  @Test
  public void delayedRemovalDoesntAffectNewlyStoredMappings() {
    myMap.put(1, 1);
    myMap.put(2, 1);
    myMap.markForDeletion(1, 2);
    myMap.put(2, 1);
    myMap.deleteMarked();
    
    assertEquals(1, myMap.size());
    assertNull(myMap.get(1));
    assertNotNull(myMap.get(2));
  }
}
