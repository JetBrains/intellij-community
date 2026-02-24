/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package pkg;

public class TestConstants {
  static final boolean T = true;
  static final boolean F = false;

  static final char C0 = '\n';
  static final char C1 = 'a';
  static final char C2 = 512;

  static final byte BMin = Byte.MIN_VALUE;
  static final byte BMax = Byte.MAX_VALUE;

  static final short SMin = Short.MIN_VALUE;
  static final short SMax = Short.MAX_VALUE;

  static final int IMin = Integer.MIN_VALUE;
  static final int IMax = Integer.MAX_VALUE;

  static final long LMin = Long.MIN_VALUE;
  static final long LMax = Long.MAX_VALUE;

  static final float FNan = Float.NaN;
  static final float FNeg = Float.NEGATIVE_INFINITY;
  static final float FPos = Float.POSITIVE_INFINITY;
  static final float FMin = Float.MIN_VALUE;
  static final float FMax = Float.MAX_VALUE;
  static final float FMinNormal = Float.MIN_NORMAL;

  static final double DNan = Double.NaN;
  static final double DNeg = Double.NEGATIVE_INFINITY;
  static final double DPos = Double.POSITIVE_INFINITY;
  static final double DMin = Double.MIN_VALUE;
  static final double DMax = Double.MAX_VALUE;
  static final double FDoubleNormal = Double.MIN_NORMAL;

  static final double PI_d = Math.PI;
  static final double PI_d_2 = Math.PI / 2D;
  static final double PI_d_3 = -Math.PI / 3D;
  static final double PI_d_4 = Math.PI / 4D;
  static final double PI_d_5 = -Math.PI / 5D;
  static final double PI_d_6 = Math.PI / 6D;
  static final double PI_d_8 = -Math.PI / 8D;
  static final double PI_d_180 = Math.PI / 180D;
  static final double PI_d_2m = Math.PI * 2D;
  static final float PI_f = (float) Math.PI;
  static final float PI_f_2 = PI_f / 2F;
  static final float PI_f_3 = -PI_f / 3F;
  static final float PI_f_4 = -PI_f / 4F;
  static final float PI_f_5 = PI_f / 5F;
  static final float PI_f_6 = -PI_f / 6F;
  static final float PI_f_8 = PI_f / 8F;
  static final float PI_f_2m = PI_f * 2F;
  static final double E = Math.E;
  static final float A = (float) Integer.MAX_VALUE;
  static final double A2 = (double) Long.MIN_VALUE;
}