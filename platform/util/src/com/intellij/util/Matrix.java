// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

/**
 * @see Vector
 */
public final class Matrix {
  public static Matrix create(int height, double... values) {
    if (values.length == 0) throw new IllegalArgumentException("no values");
    if (height <= 0) throw new IllegalArgumentException("unexpected height");
    int width = values.length / height;
    if (width * height != values.length) throw new IllegalArgumentException("unexpected amount of values");
    return new Matrix(width, height, values.clone());
  }

  public static Matrix createIdentity(int size) {
    if (size <= 0) throw new IllegalArgumentException("unexpected size");
    double[] array = new double[size * size];
    for (int index = 0, i = 0; i < size; i++, index += size + 1) array[index] = 1;
    return new Matrix(size, size, array);
  }

  public static Matrix createColumn(Vector vector) {
    return new Matrix(1, vector.getSize(), vector);
  }

  public static Matrix createRow(Vector vector) {
    return new Matrix(vector.getSize(), 1, vector);
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof Matrix) {
      Matrix matrix = (Matrix)object;
      return width == matrix.width && height == matrix.height && vector.equals(matrix.vector);
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Matrix ").append(height).append("x").append(width).append(" { ");
    for (int i = 0, h = 0; h < height; h++) {
      if (h != 0) sb.append(", ");
      sb.append("{");
      for (int w = 0; w < width; w++, i++) {
        if (w != 0) sb.append(", ");
        sb.append(vector.get(i));
      }
      sb.append("}");
    }
    return sb.append(" }").toString();
  }

  public double get(int column, int row) {
    validate(column, width);
    validate(row, height);
    return vector.get(column + row * width);
  }

  public int getColumns() {
    return width;
  }

  public Vector getColumn(final int column) {
    validate(column, width);
    return new Vector.Modified(vector) {
      @Override
      public double get(int row) {
        validate(row, height);
        return super.get(column + row * width);
      }

      @Override
      public int getSize() {
        return height;
      }
    };
  }

  public int getRows() {
    return height;
  }

  public Vector getRow(final int row) {
    validate(row, height);
    return new Vector.Modified(vector) {
      @Override
      public double get(int column) {
        validate(column, width);
        return super.get(column + row * width);
      }

      @Override
      public int getSize() {
        return width;
      }
    };
  }

  public Matrix plus(Matrix matrix) {
    if (getColumns() != matrix.getColumns()) throw new IllegalArgumentException("columns mismatch");
    if (getRows() != matrix.getRows()) throw new IllegalArgumentException("rows mismatch");
    return new Matrix(width, height, vector.plus(matrix.vector));
  }

  public Matrix minus(Matrix matrix) {
    if (getColumns() != matrix.getColumns()) throw new IllegalArgumentException("columns mismatch");
    if (getRows() != matrix.getRows()) throw new IllegalArgumentException("rows mismatch");
    return new Matrix(width, height, vector.minus(matrix.vector));
  }

  public Matrix multiply(double value) {
    return new Matrix(width, height, vector.multiply(value));
  }

  public Matrix multiply(Matrix matrix) {
    if (getColumns() != matrix.getRows()) throw new IllegalArgumentException("columns mismatch rows");
    int width = matrix.getColumns();
    int height = getRows();
    double[] result = new double[width * height];
    for (int i = 0, h = 0; h < height; h++) {
      Vector row = getRow(h);
      for (int w = 0; w < width; w++, i++) {
        result[i] = row.multiply(matrix.getColumn(w));
      }
    }
    return new Matrix(width, height, result);
  }

  public Vector multiply(Vector vector) {
    if (getColumns() != vector.getSize()) throw new IllegalArgumentException("columns mismatch length");
    double[] result = new double[getRows()];
    for (int i = 0; i < result.length; i++) result[i] = getRow(i).multiply(vector);
    return new Vector(result);
  }

  public double determinant() {
    if (width != height) throw new IllegalArgumentException("not a square");
    if (width == 1) return vector.get(0);
    if (width == 2) return vector.get(0) * vector.get(3) - vector.get(1) * vector.get(2);
    double result = 0;
    for (int i = 0; i < width; i++) {
      double value = vector.get(i) * exclude(i, 0).determinant();
      result -= isEven(i) ? -value : value;
    }
    return result;
  }

  public Matrix transpose() {
    double[] result = new double[vector.getSize()];
    for (int i = 0, w = 0; w < width; w++) {
      for (int h = 0; h < height; h++, i++) {
        result[i] = get(w, h);
      }
    }
    //noinspection SuspiciousNameCombination
    return new Matrix(height, width, result);
  }

  public Matrix inverse() {
    double value = determinant();
    if (value == 0) throw new IllegalArgumentException("determinant is 0");
    return cofactor().transpose().multiply(1 / value);
  }

  private Matrix exclude(int column, int row) {
    validate(column, width);
    validate(row, height);

    int width = getColumns() - 1;
    if (width == 0) throw new IllegalArgumentException("cannot exclude last column");

    int height = getRows() - 1;
    if (height == 0) throw new IllegalArgumentException("cannot exclude last row");

    double[] result = new double[width * height];
    int index = 0;
    for (int i = 0, h = 0; h <= height; h++) {
      for (int w = 0; w <= width; w++, i++) {
        if (w != column && h != row) {
          result[index++] = vector.get(i);
        }
      }
    }
    return new Matrix(width, height, result);
  }

  private Matrix cofactor() {
    if (width != height) throw new IllegalArgumentException("not a square");
    double[] result = new double[vector.getSize()];
    for (int i = 0, h = 0; h < height; h++) {
      for (int w = 0; w < width; w++, i++) {
        double value = exclude(w, h).determinant();
        result[i] = isEven(w) != isEven(h) ? -value : value;
      }
    }
    return new Matrix(width, height, result);
  }


  private final int width;
  private final int height;
  private final Vector vector;

  private Matrix(int width, int height, double... values) {
    this(width, height, new Vector(values));
  }

  private Matrix(int width, int height, Vector vector) {
    this.width = width;
    this.height = height;
    this.vector = vector;
  }

  private static boolean isEven(int i) {
    return i % 2 == 0;
  }

  private static void validate(int index, int max) {
    if (index < 0 || max <= index) throw new ArrayIndexOutOfBoundsException(index);
  }
}
