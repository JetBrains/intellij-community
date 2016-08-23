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

import com.intellij.openapi.editor.impl.softwrap.mapping.CompositeDataProvider;
import com.intellij.openapi.editor.impl.softwrap.mapping.DataProvider;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Denis Zhdanov
 * @since 08/31/2010
 */
public class CompositeDataProviderTest {

  private enum DataType implements Comparable<DataType> { ONE, TWO }

  private CompositeDataProvider myProvider;  

  @Before
  public void setUp() {
    myProvider = new CompositeDataProvider();
  }

  @Test
  public void unequalNumberOfDataEntries() {
    myProvider = new CompositeDataProvider(
      new TestDataProvider<>(DataType.ONE, 1, 3, 5, 7),
      new TestDataProvider<>(DataType.TWO, 2, 4)
    );

    assertTrue(myProvider.hasData());
    Pair<DataType, Object> data = myProvider.getData();
    assertSame(data.first, DataType.ONE);
    assertSame(1, myProvider.getSortingKey());

    assertTrue(myProvider.hasData());
    assertTrue(myProvider.next());
    data = myProvider.getData();
    assertSame(data.first, DataType.TWO);
    assertSame(2, myProvider.getSortingKey());

    assertTrue(myProvider.hasData());
    assertTrue(myProvider.next());
    data = myProvider.getData();
    assertSame(data.first, DataType.ONE);
    assertSame(3, myProvider.getSortingKey());

    assertTrue(myProvider.hasData());
    assertTrue(myProvider.next());
    data = myProvider.getData();
    assertSame(data.first, DataType.TWO);
    assertSame(4, myProvider.getSortingKey());

    assertTrue(myProvider.hasData());
    assertTrue(myProvider.next());
    data = myProvider.getData();
    assertSame(data.first, DataType.ONE);
    assertSame(5, myProvider.getSortingKey());

    assertTrue(myProvider.hasData());
    assertTrue(myProvider.next());
    data = myProvider.getData();
    assertSame(data.first, DataType.ONE);
    assertSame(7, myProvider.getSortingKey());

    assertFalse(myProvider.next());
    assertFalse(myProvider.hasData());
  }

  @Test
  public void emptyProviders() {
    myProvider = new CompositeDataProvider(
      new TestDataProvider<>(DataType.ONE),
      new TestDataProvider<>(DataType.TWO)
    );

    assertFalse(myProvider.hasData());
    assertFalse(myProvider.next());
  }

  @Test
  public void emptyProvidersAfterAdvance() {
    myProvider = new CompositeDataProvider(
      new TestDataProvider<>(DataType.ONE, 1, 3, 5),
      new TestDataProvider<>(DataType.TWO, 2, 4)
    );

    myProvider.advance(4);
    assertTrue(myProvider.hasData());
    Pair<DataType, Object> data = myProvider.getData();
    assertSame(data.first, DataType.TWO);
    assertSame(4, myProvider.getSortingKey());

    assertTrue(myProvider.next());
    assertTrue(myProvider.hasData());
    data = myProvider.getData();
    assertSame(data.first, DataType.ONE);
    assertSame(5, myProvider.getSortingKey());

    assertFalse(myProvider.next());
    assertFalse(myProvider.hasData());
  }

  @Test
  public void multipleAdvances() {
    myProvider = new CompositeDataProvider(
      new TestDataProvider<>(DataType.ONE, 1, 3, 5),
      new TestDataProvider<>(DataType.TWO, 2, 4)
    );

    myProvider.advance(2);
    assertTrue(myProvider.hasData());
    Pair<DataType, Object> data = myProvider.getData();
    assertSame(data.first, DataType.TWO);
    assertSame(2, myProvider.getSortingKey());

    myProvider.advance(4);
    assertTrue(myProvider.hasData());
    data = myProvider.getData();
    assertSame(data.first, DataType.TWO);
    assertSame(4, myProvider.getSortingKey());

    myProvider.advance(6);

    assertFalse(myProvider.next());
    assertFalse(myProvider.hasData());
  }

  @Test
  public void nonEmptyProvidersAfterAdvance() {
    myProvider = new CompositeDataProvider(
      new TestDataProvider<>(DataType.ONE, 1, 3, 5),
      new TestDataProvider<>(DataType.TWO, 2, 4)
    );

    myProvider.advance(6);
    assertFalse(myProvider.hasData());
    assertFalse(myProvider.next());
  }

  private static class TestDataProvider<K extends Comparable<? super K>> implements DataProvider<K, Object> {

    private final K myKey;
    private final int[] mySortingKeys;
    private int myIndex;

    private TestDataProvider(@NotNull K key, @NotNull int ... sortingKeys) {
      myKey = key;
      mySortingKeys = sortingKeys;
    }

    @Nullable
    @Override
    public Pair<K, Object> getData() {
      if (myIndex < mySortingKeys.length) {
        return new Pair<>(myKey, new Object());
      }
      return null;
    }

    @Override
    public boolean next() {
      return ++myIndex < mySortingKeys.length;
    }

    @Override
    public void advance(int sortingKey) {
      while (myIndex < mySortingKeys.length && mySortingKeys[myIndex] < sortingKey) {
        myIndex++;
      }
    }

    @Override
    public int getSortingKey() {
      return mySortingKeys[myIndex];
    }
  }
}
