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

/**
 * @author Sergey.Malenkov
 * @see Matrix
 */
public class Vector {
  public static Vector create(double... values) {
    if (values.length == 0) throw new IllegalArgumentException("no values");
    return new Vector(values.clone());
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (object instanceof Vector) {
      Vector vector = (Vector)object;
      int size = vector.getSize();
      if (size == getSize()) {
        for (int i = 0; i < size; i++) {
          if (Double.doubleToLongBits(get(i)) != Double.doubleToLongBits(vector.get(i))) return false;
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    int size = getSize();
    StringBuilder sb = new StringBuilder();
    sb.append("Vector ").append(size).append(" { ");
    for (int i = 0; i < size; i++) {
      if (i != 0) sb.append(", ");
      sb.append(get(i));
    }
    return sb.append(" }").toString();
  }

  public double get(int index) {
    return array[index];
  }

  public int getSize() {
    return array.length;
  }

  public Vector plus(Vector vector) {
    int size = vector.getSize();
    if (size != getSize()) throw new IllegalArgumentException("sizes mismatch");
    double[] result = toArray();
    for (int i = 0; i < result.length; i++) result[i] += vector.get(i);
    return new Vector(result);
  }

  public Vector minus(Vector vector) {
    int size = vector.getSize();
    if (size != getSize()) throw new IllegalArgumentException("sizes mismatch");
    double[] result = toArray();
    for (int i = 0; i < result.length; i++) result[i] -= vector.get(i);
    return new Vector(result);
  }

  public Vector multiply(double value) {
    double[] result = toArray();
    for (int i = 0; i < result.length; i++) result[i] *= value;
    return new Vector(result);
  }

  public Vector multiply(Matrix matrix) {
    int size = matrix.getRows();
    if (size != getSize()) throw new IllegalArgumentException("size mismatch rows");
    double[] result = new double[matrix.getColumns()];
    for (int i = 0; i < result.length; i++) result[i] = multiply(matrix.getColumn(i));
    return new Vector(result);
  }

  public double multiply(Vector vector) {
    int size = vector.getSize();
    if (size != getSize()) throw new IllegalArgumentException("sizes mismatch");
    double result = 0;
    for (int i = 0; i < size; i++) result += get(i) * vector.get(i);
    return result;
  }

  public double length() {
    return Math.sqrt(multiply(this));
  }


  private final double[] array;

  Vector(double[] array) {
    this.array = array;
  }

  double[] toArray() {
    return array.clone();
  }


  static class Modified extends Vector {
    Modified(Vector vector) {
      super(vector.array);
    }

    @Override
    double[] toArray() {
      int size = getSize();
      double[] result = new double[size];
      for (int i = 0; i < result.length; i++) result[i] = get(i);
      return result;
    }
  }
}
