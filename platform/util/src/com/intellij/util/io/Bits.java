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

/*
 * @author max
 */
package com.intellij.util.io;

/**
 * Barely, a copy of java.io.Bits, which is for unknown reason package local.
 */
public class Bits {

    /*
     * Methods for unpacking primitive values from byte arrays starting at
     * given offsets.
     */

    public static boolean getBoolean(byte[] b, int off) {
	return b[off] != 0;
    }

    public static char getChar(byte[] b, int off) {
	return (char) (((b[off + 1] & 0xFF) << 0) +
		       ((b[off + 0] & 0xFF) << 8));
    }

    public static short getShort(byte[] b, int off) {
	return (short) (((b[off + 1] & 0xFF) << 0) +
			((b[off + 0] & 0xFF) << 8));
    }

    public static int getInt(byte[] b, int off) {
	return ((b[off + 3] & 0xFF) << 0) +
	       ((b[off + 2] & 0xFF) << 8) +
	       ((b[off + 1] & 0xFF) << 16) +
	       ((b[off + 0] & 0xFF) << 24);
    }

    public static float getFloat(byte[] b, int off) {
	int i = ((b[off + 3] & 0xFF) << 0) +
		((b[off + 2] & 0xFF) << 8) +
		((b[off + 1] & 0xFF) << 16) +
		((b[off + 0] & 0xFF) << 24);
	return Float.intBitsToFloat(i);
    }

    public static long getLong(byte[] b, int off) {
	return ((b[off + 7] & 0xFFL) << 0) +
	       ((b[off + 6] & 0xFFL) << 8) +
	       ((b[off + 5] & 0xFFL) << 16) +
	       ((b[off + 4] & 0xFFL) << 24) +
	       ((b[off + 3] & 0xFFL) << 32) +
	       ((b[off + 2] & 0xFFL) << 40) +
	       ((b[off + 1] & 0xFFL) << 48) +
	       ((b[off + 0] & 0xFFL) << 56);
    }

    public static double getDouble(byte[] b, int off) {
	long j = ((b[off + 7] & 0xFFL) << 0) +
		 ((b[off + 6] & 0xFFL) << 8) +
		 ((b[off + 5] & 0xFFL) << 16) +
		 ((b[off + 4] & 0xFFL) << 24) +
		 ((b[off + 3] & 0xFFL) << 32) +
		 ((b[off + 2] & 0xFFL) << 40) +
		 ((b[off + 1] & 0xFFL) << 48) +
		 ((b[off + 0] & 0xFFL) << 56);
	return Double.longBitsToDouble(j);
    }

    /*
     * Methods for packing primitive values into byte arrays starting at given
     * offsets.
     */

    public static void putBoolean(byte[] b, int off, boolean val) {
	b[off] = (byte) (val ? 1 : 0);
    }

    public static void putChar(byte[] b, int off, char val) {
	b[off + 1] = (byte) (val >>> 0);
	b[off + 0] = (byte) (val >>> 8);
    }

    public static void putShort(byte[] b, int off, short val) {
	b[off + 1] = (byte) (val >>> 0);
	b[off + 0] = (byte) (val >>> 8);
    }

    public static void putInt(byte[] b, int off, int val) {
	b[off + 3] = (byte) (val >>> 0);
	b[off + 2] = (byte) (val >>> 8);
	b[off + 1] = (byte) (val >>> 16);
	b[off + 0] = (byte) (val >>> 24);
    }

    public static void putFloat(byte[] b, int off, float val) {
	int i = Float.floatToIntBits(val);
	b[off + 3] = (byte) (i >>> 0);
	b[off + 2] = (byte) (i >>> 8);
	b[off + 1] = (byte) (i >>> 16);
	b[off + 0] = (byte) (i >>> 24);
    }

    public static void putLong(byte[] b, int off, long val) {
	b[off + 7] = (byte) (val >>> 0);
	b[off + 6] = (byte) (val >>> 8);
	b[off + 5] = (byte) (val >>> 16);
	b[off + 4] = (byte) (val >>> 24);
	b[off + 3] = (byte) (val >>> 32);
	b[off + 2] = (byte) (val >>> 40);
	b[off + 1] = (byte) (val >>> 48);
	b[off + 0] = (byte) (val >>> 56);
    }

    public static void putDouble(byte[] b, int off, double val) {
	long j = Double.doubleToLongBits(val);
	b[off + 7] = (byte) (j >>> 0);
	b[off + 6] = (byte) (j >>> 8);
	b[off + 5] = (byte) (j >>> 16);
	b[off + 4] = (byte) (j >>> 24);
	b[off + 3] = (byte) (j >>> 32);
	b[off + 2] = (byte) (j >>> 40);
	b[off + 1] = (byte) (j >>> 48);
	b[off + 0] = (byte) (j >>> 56);
    }
}
