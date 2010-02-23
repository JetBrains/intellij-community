/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.containers;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @author Dmitry.Shtukenberg
 */
public final class VariableWidthIntArray implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.containers.VariableWidthIntArray");

  private int[] intArray = null;
  private short[] shortArray = null;
  private byte[] byteArray = null;

  private final int minValue;
  private final int maxValue;

  static final int INT = 1;
  static final int SHORT = 2;
  static final int BYTE = 3;
  private final int arrayType;

  public VariableWidthIntArray( int minValue, int maxValue, int initialCapacity ) {
    this.minValue = minValue;
    this.maxValue = maxValue;

    if( minValue < Short.MIN_VALUE || maxValue > Short.MAX_VALUE ) {
      intArray = new int[ bestSize(initialCapacity) ];
      arrayType = INT;
    }
    else if( minValue < Byte.MIN_VALUE || maxValue > Byte.MAX_VALUE ) {
      shortArray = new short[ bestSize(initialCapacity) ];
      arrayType = SHORT;
    }
    else {
      byteArray = new byte[ bestSize(initialCapacity) ];
      arrayType = BYTE;
    }
  }

  static int bestSize( int maxSize ) {
    int newSize = (int)(((double)maxSize) * 5 / 4);
    return newSize <= maxSize ? maxSize + 1 : newSize;
  }

  public void ensureArraySize( int index ) {
    if( intArray != null && intArray.length <= index ) {
      int[] tmp = new int[ bestSize(index) ];
      System.arraycopy( intArray, 0, tmp, 0, intArray.length );
      intArray = tmp;
    }
    else if( shortArray != null && shortArray.length <= index ) {
      short[] tmp = new short[ bestSize(index) ];
      System.arraycopy( shortArray, 0, tmp, 0, shortArray.length );
      shortArray = tmp;
    }
    else if( byteArray.length <= index ) {
      byte[] tmp = new byte[ bestSize(index) ];
      System.arraycopy( byteArray, 0, tmp, 0, byteArray.length );
      byteArray = tmp;
    }
  }

  public int get( int index ) {
    switch( arrayType ) {
      case INT: return intArray[index];
      case SHORT: return shortArray[index];
      case BYTE: return byteArray[index];
    }
    LOG.error("No array allocated");
    return 0;
  }

  public void put( int index, int value ) {
    if( value < minValue || value > maxValue ) {
      LOG.error("Value out of domain");
    }

    switch( arrayType ) {
      case INT: intArray[index] = value; return;
      case SHORT: shortArray[index] = (short)value; return;
      case BYTE: byteArray[index] = (byte)value; return;
    }

    LOG.error("No array allocated");
  }

  public Object clone() throws CloneNotSupportedException {
    VariableWidthIntArray arr = (VariableWidthIntArray)super.clone();
    if( intArray != null ) { arr.intArray = intArray.clone(); }
    if( shortArray != null ) { arr.shortArray = shortArray.clone(); }
    if( byteArray != null ) { arr.byteArray = byteArray.clone(); }
    return arr;
  }

  public void arraycopy(int[] src, int from, int to, int count) {
    for( int i = 0; i < count; i++ )
      put( i + to, src[i + from] );
  }

  public void move(int from, int to, int count) {
    switch( arrayType ) {
      case INT: System.arraycopy( intArray, from, intArray, to, count ); break;
      case SHORT: System.arraycopy( shortArray, from, shortArray, to, count ); break;
      case BYTE: System.arraycopy( byteArray, from, byteArray, to, count ); break;
      default:
        LOG.error("Invalid array type");
    }
  }

  public boolean arrayequal( VariableWidthIntArray src, int srcfrom, int from, int count ) {
    if( src.arrayType != arrayType ) return false;
    for( int i = 0; i < count; i++ )
      if( get(from+i) != src.get(srcfrom+i)) return false;
    return true;
  }
}
