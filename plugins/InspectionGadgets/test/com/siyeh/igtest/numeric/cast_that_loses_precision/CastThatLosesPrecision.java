/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.igtest.numeric.cast_that_loses_precision;

public class CastThatLosesPrecision
{
    public CastThatLosesPrecision()
    {
    }

    public void fooBar(long l, double d, float f)
    {
        byte b;
        int i;
        char ch;




        i = (int) f;
        System.out.println("i = " + i);
        ch = (char) d;
        System.out.println("ch = " + ch);
        i = (int) d;
        System.out.println("i = " + i);
        i = (int) l;
        System.out.println("i = " + i);
        b = (byte) l;
        System.out.println("b = " + b);

        l = (long) d;
        System.out.println("l = " + l);
        l = (long) f;
        System.out.println("l = " + l);

        d = (double) f;
        System.out.println("d = " + d);

        f = (float) d;
        System.out.println("f = " + f);
    }

    public void barFoo(long l) {
        byte b;
        int i;
        char ch;


        i = (int) 0.0f;
        System.out.println("i = " + i);
        ch = (char) 0.0;
        System.out.println("ch = " + ch);
        i = (int) 0.0;
        System.out.println("i = " + i);
        i = (int) 0L;
        System.out.println("i = " + i);
        b = (byte) l;
        System.out.println("b = " + b);

        l = (long) 0.0;
        System.out.println("l = " + l);
        l = (long) 0.0f;
        System.out.println("l = " + l);

        final double d = (double)0.0f;
        System.out.println("d = " + d);

        final float f = (float)0.0;
        System.out.println("f = " + f);
    }


  private long aLong = 2L;
  private double d = 1.0;

  @Override
  public int hashCode() {
    int result = (int) (aLong ^ (aLong >>> 32));
    long temp = d != +0.0d ? (int) d : 0L;
    result = 31 * result + (int) (temp ^ temp >>> 32);
    return result;
  }

  void testNegativeOnly(long longNumberOfAgents) {
    if (longNumberOfAgents > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Too many agents: " + longNumberOfAgents);
    }
    int intNumberOfAgents = (int)longNumberOfAgents;
    System.out.println(intNumberOfAgents);
  }

  void testBoundsCheck(long longNumberOfAgents) {
    if (longNumberOfAgents < 0) {
      throw new IllegalArgumentException("Negative is not allowed");
    }
    if (longNumberOfAgents > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Too many agents: " + longNumberOfAgents);
    }
    int intNumberOfAgents = (int)longNumberOfAgents;
    System.out.println(intNumberOfAgents);
  }
}
