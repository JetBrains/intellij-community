/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import junit.framework.TestCase;

/**
 * @author Sergey.Malenkov
 */
public class VectorTest extends TestCase {
  public void testCreate() {
    Vector vector = Vector.create(0, 1, 2, 3, 4);
    int size = vector.getSize();
    assertEquals(5, size);
    for (int i = 0; i < size; i++) {
      assertEquals((double)i, vector.get(i));
    }
  }

  public void testCreateEmpty() {
    try {
      fail("Created " + Vector.create());
    }
    catch (Exception ignored) {
    }
  }

  public void testCreateNull() {
    try {
      fail("Created " + Vector.create((double[])null));
    }
    catch (Exception ignored) {
    }
  }

  public void testPlus() {
    // see http://ru.onlinemschool.com/math/library/vector/add_subtract/
    assertEquals(Vector.create(5, 10), Vector.create(1, 2).plus(Vector.create(4, 8)));
    assertEquals(Vector.create(5, 10, 6), Vector.create(1, 2, 5).plus(Vector.create(4, 8, 1)));
    assertEquals(Vector.create(5, 10, 6, -11), Vector.create(1, 2, 5, 9).plus(Vector.create(4, 8, 1, -20)));
  }

  public void testPlusWrong() {
    try {
      fail("Created " + Vector.create(1, 2).plus(Vector.create(1, 2, 3)));
    }
    catch (Exception ignored) {
    }
  }

  public void testMinus() {
    // see http://ru.onlinemschool.com/math/library/vector/add_subtract/
    assertEquals(Vector.create(-3, -6), Vector.create(1, 2).minus(Vector.create(4, 8)));
    assertEquals(Vector.create(-3, -6, 4), Vector.create(1, 2, 5).minus(Vector.create(4, 8, 1)));
    assertEquals(Vector.create(-3, -6, 4, 0, 3), Vector.create(1, 2, 5, -1, 5).minus(Vector.create(4, 8, 1, -1, 2)));
  }

  public void testMinusWrong() {
    try {
      fail("Created " + Vector.create(1, 2).minus(Vector.create(1, 2, 3)));
    }
    catch (Exception ignored) {
    }
  }

  public void testMultiplyByValue() {
    // see http://ru.onlinemschool.com/math/library/vector/multiply3/
    assertEquals(Vector.create(3, 6), Vector.create(1, 2).multiply(3));
    assertEquals(Vector.create(-2, -4, 10), Vector.create(1, 2, -5).multiply(-2));
  }

  public void testMultiplyByMatrix() {
    Vector vector = Vector.create(1, 2, 3);
    assertEquals(vector, vector.multiply(Matrix.createIdentity(3)));

    Matrix matrix = Matrix.create(3, /*row:*/ 11, 21, /*row:*/ 12, 22, /*row:*/ 13, 23);
    Vector result = vector.multiply(matrix);
    assertEquals(Vector.create(74, 134), result);
    assertEquals(matrix.getColumns(), result.getSize());
    assertEquals(Matrix.createRow(result), Matrix.createRow(vector).multiply(matrix));
  }

  public void testMultiplyByVector() {
    assertEquals(Vector.create(4, 8).multiply(Vector.create(1, 2)), Vector.create(1, 2).multiply(Vector.create(4, 8)));
    // see http://ru.onlinemschool.com/math/library/vector/multiply/
    assertEquals(20.0, Vector.create(1, 2).multiply(Vector.create(4, 8)));
    assertEquals(15.0, Vector.create(1, 2, -5).multiply(Vector.create(4, 8, 1)));
    assertEquals(11.0, Vector.create(1, 2, -5, 2).multiply(Vector.create(4, 8, 1, -2)));
  }

  public void testMultiplyByVectorWrong() {
    try {
      fail("Created " + Vector.create(1, 2).minus(Vector.create(1, 2, 3)));
    }
    catch (Exception ignored) {
    }
  }


  public void testLength() {
    // see http://ru.onlinemschool.com/math/library/vector/length/
    assertEquals(5.0, Vector.create(3, -4).length());
    assertEquals(6.0, Vector.create(2, 4, 4).length());
  }
}
