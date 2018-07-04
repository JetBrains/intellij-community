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
public class MatrixTest extends TestCase {
  public void testCreate() {
    Matrix matrix = Matrix.create(3, /*row:*/ 11, 21, /*row:*/ 12, 22, /*row:*/ 13, 23);
    int width = matrix.getColumns();
    assertEquals(2, width);
    int height = matrix.getRows();
    assertEquals(3, height);
    for (int w = 0; w < width; w++) {
      for (int h = 0; h < height; h++) {
        assertEquals((double)(10 * (w + 1) + (h + 1)), matrix.get(w, h));
      }
    }
    assertEquals(Vector.create(11, 21), matrix.getRow(0));
    assertEquals(Vector.create(12, 22), matrix.getRow(1));
    assertEquals(Vector.create(13, 23), matrix.getRow(2));
    assertEquals(Vector.create(11, 12, 13), matrix.getColumn(0));
    assertEquals(Vector.create(21, 22, 23), matrix.getColumn(1));
  }

  public void testCreateEmpty() {
    try {
      fail("Created " + Matrix.create(0));
    }
    catch (Exception ignored) {
    }
  }

  public void testCreateNull() {
    try {
      fail("Created " + Matrix.create(0, (double[])null));
    }
    catch (Exception ignored) {
    }
  }

  public void testCreateZeroWidth() {
    try {
      fail("Created " + Matrix.create(2, 0));
    }
    catch (Exception ignored) {
    }
  }

  public void testCreateZeroHeight() {
    try {
      fail("Created " + Matrix.create(0, 0));
    }
    catch (Exception ignored) {
    }
  }

  public void testCreateIdentity() {
    Matrix matrix = Matrix.createIdentity(3);
    int width = matrix.getColumns();
    assertEquals(3, width);
    int height = matrix.getRows();
    assertEquals(3, height);
    for (int w = 0; w < width; w++) {
      for (int h = 0; h < height; h++) {
        assertEquals((double)(w == h ? 1 : 0), matrix.get(w, h));
      }
    }
    Vector vector0 = Vector.create(1, 0, 0);
    assertEquals(vector0, matrix.getRow(0));
    assertEquals(vector0, matrix.getColumn(0));
    Vector vector1 = Vector.create(0, 1, 0);
    assertEquals(vector1, matrix.getRow(1));
    assertEquals(vector1, matrix.getColumn(1));
    Vector vector2 = Vector.create(0, 0, 1);
    assertEquals(vector2, matrix.getRow(2));
    assertEquals(vector2, matrix.getColumn(2));
  }

  public void testCreateIdentityWrong() {
    try {
      fail("Created " + Matrix.createIdentity(-1));
    }
    catch (Exception ignored) {
    }
  }

  public void testCreateColumn() {
    Vector vector = Vector.create(1, 2, 3);
    Matrix matrix = Matrix.createColumn(vector);
    assertEquals(1, matrix.getColumns());
    assertEquals(3, matrix.getRows());
    assertEquals(vector, matrix.getColumn(0));
    assertEquals(Vector.create(1), matrix.getRow(0));
    assertEquals(Vector.create(2), matrix.getRow(1));
    assertEquals(Vector.create(3), matrix.getRow(2));
  }

  public void testCreateRow() {
    Vector vector = Vector.create(1, 2, 3);
    Matrix matrix = Matrix.createRow(vector);
    assertEquals(3, matrix.getColumns());
    assertEquals(1, matrix.getRows());
    assertEquals(vector, matrix.getRow(0));
    assertEquals(Vector.create(1), matrix.getColumn(0));
    assertEquals(Vector.create(2), matrix.getColumn(1));
    assertEquals(Vector.create(3), matrix.getColumn(2));
  }

  public void testPlus() {
    // see http://ru.onlinemschool.com/math/library/matrix/add_subtract/
    assertEquals(Matrix.create(2, // rows
                               7, 3,
                               6, 4), Matrix.create(2, // rows
                                                    4, 2,
                                                    9, 0).plus(Matrix.create(2, // rows
                                                                             3, 1,
                                                                             -3, 4)));
  }

  public void testPlusWrong() {
    try {
      fail("Created " + Matrix.create(1, 0).plus(Matrix.create(2, 1, 1, 1, 1)));
    }
    catch (Exception ignored) {
    }
  }

  public void testMinus() {
    // see http://ru.onlinemschool.com/math/library/matrix/add_subtract/
    assertEquals(Matrix.create(2, // rows
                               1, 1,
                               12, -4), Matrix.create(2, // rows
                                                      4, 2,
                                                      9, 0).minus(Matrix.create(2, // rows
                                                                                3, 1,
                                                                                -3, 4)));
  }

  public void testMinusWrong() {
    try {
      fail("Created " + Matrix.create(1, 0).minus(Matrix.create(2, 1, 1, 1, 1)));
    }
    catch (Exception ignored) {
    }
  }

  public void testMultiplyByValue() {
    // see http://ru.onlinemschool.com/math/library/matrix/multiply1/
    assertEquals(Matrix.create(2, // rows
                               20, 10,
                               45, 0), Matrix.create(2, // rows
                                                     4, 2,
                                                     9, 0).multiply(5));
    assertEquals(Matrix.create(3, // rows
                               -4, 4,
                               2, -0.0, // fix
                               -10, 2), Matrix.create(3, // rows
                                                      2, -2,
                                                      -1, 0,
                                                      5, -1).multiply(-2));
  }

  public void testMultiplyByMatrix() {
    // see http://ru.onlinemschool.com/math/library/matrix/multiply/
    assertEquals(Matrix.create(2, // rows
                               6, 12,
                               27, 9), Matrix.create(2, // rows
                                                     4, 2,
                                                     9, 0).multiply(Matrix.create(2, // rows
                                                                                  3, 1,
                                                                                  -3, 4)));
    assertEquals(Matrix.create(3, // rows
                               7, -2, 19,
                               -15, 3, -18,
                               23, -4, 17), Matrix.create(3, // rows
                                                          2, 1,
                                                          -3, 0,
                                                          4, -1).multiply(Matrix.create(2, // rows
                                                                                        5, -1, 6,
                                                                                        -3, 0, 7)));
  }

  public void testMultiplyByVector() {
    Vector vector = Vector.create(1, 2, 3);
    assertEquals(vector, Matrix.createIdentity(3).multiply(vector));

    Matrix matrix = Matrix.create(2, /*row:*/ 11, 12, 13, /*row:*/ 21, 22, 23);
    Vector result = matrix.multiply(vector);
    assertEquals(Vector.create(74, 134), result);
    assertEquals(matrix.getRows(), result.getSize());
    assertEquals(Matrix.createColumn(result), matrix.multiply(Matrix.createColumn(vector)));
  }

  public void testDeterminant() {
    // see http://ru.onlinemschool.com/math/library/matrix/determinant/
    assertEquals(33.0, Matrix.create(2, 5, 7, -4, 1).determinant());
    assertEquals(97.0, Matrix.create(3, 5, 7, 1, -4, 1, 0, 2, 0, 3).determinant());
    assertEquals(6.0, Matrix.create(3, 2, 4, 1, 0, 2, 1, 2, 1, 1).determinant());
    assertEquals(0.0, Matrix.create(4, 2, 4, 1, 1, 0, 2, 0, 0, 2, 1, 1, 3, 4, 0, 2, 3).determinant());
  }

  public void testDeterminantZero() {
    assertEquals(0.0, Matrix.create(3, 0, 0, 0, 1, 2, 3, 4, 5, 6).determinant()); // 1 zero row
    assertEquals(0.0, Matrix.create(3, 1, 1, 1, 1, 1, 1, 1, 2, 3).determinant()); // 2 equal rows
    assertEquals(0.0, Matrix.create(3, 1, 1, 1, 2, 2, 2, 1, 2, 3).determinant()); // 2 proportional rows
  }

  public void testDeterminantIdentity() {
    assertEquals(1.0, Matrix.createIdentity(1).determinant());
    assertEquals(1.0, Matrix.createIdentity(2).determinant());
    assertEquals(1.0, Matrix.createIdentity(3).determinant());
    assertEquals(1.0, Matrix.createIdentity(4).determinant());
    assertEquals(1.0, Matrix.createIdentity(5).determinant());
  }

  public void testDeterminantInverse() {
    Matrix matrix = Matrix.create(2, 5, 7, -4, 1);
    assertEquals(matrix.determinant(), 1 / matrix.inverse().determinant());
  }

  public void testDeterminantTranspose() {
    Matrix matrix = Matrix.create(2, 5, 7, -4, 1);
    assertEquals(matrix.determinant(), matrix.transpose().determinant());
  }

  public void testTranspose() {
    Matrix matrix = Matrix.create(3, /*row:*/ 11, 12, /*row:*/ 21, 22, /*row:*/ 31, 32);
    Matrix result = matrix.transpose();
    assertEquals(matrix.getColumns(), result.getRows());
    assertEquals(matrix.getColumn(0), result.getRow(0));
    assertEquals(matrix.getColumn(1), result.getRow(1));
    assertEquals(matrix.getRows(), result.getColumns());
    assertEquals(matrix.getRow(0), result.getColumn(0));
    assertEquals(matrix.getRow(1), result.getColumn(1));
    assertEquals(matrix.getRow(2), result.getColumn(2));
    // see http://ru.onlinemschool.com/math/library/matrix/transpose/
    assertEquals(Matrix.create(2, 4, 9, 2, 0), Matrix.create(2, 4, 2, 9, 0).transpose());
    assertEquals(Matrix.create(2, 2, -3, 4, 1, 0, -1), Matrix.create(3, 2, 1, -3, 0, 4, -1).transpose());
    assertEquals(Matrix.create(3, 2, 1, -3, 0, 4, -1), Matrix.create(2, 2, -3, 4, 1, 0, -1).transpose());
  }

  public void testTransposeIdentity() {
    Matrix matrix = Matrix.create(3, /*row:*/ 11, 12, 13, /*row:*/ 21, 22, 23, /*row:*/ 31, 32, 33);
    assertEquals(matrix, Matrix.createIdentity(3).multiply(matrix));
    assertEquals(matrix, matrix.multiply(Matrix.createIdentity(3)));
  }

  public void testInverse() {
    Matrix matrix = Matrix.create(2, 5, 7, -4, 1);
    assertEquals(matrix, matrix.inverse().inverse());
    assertEquals(matrix.transpose().inverse(), matrix.inverse().transpose());
    // see http://ru.onlinemschool.com/math/library/matrix/inverse/
    assertEquals(Matrix.create(3, //rows
                               1 / 6.0, -1 / 2.0, 1 / 3.0,
                               1 / 3.0, 0, -1 / 3.0,
                               -2 / 3.0, 1, 2 / 3.0), Matrix.create(3, //rows
                                                                    2, 4, 1,
                                                                    0, 2, 1,
                                                                    2, 1, 1).inverse());
  }
}
