package com.siyeh.igtest.abstraction.magic_number;

import java.awt.*;
import java.util.*;
import java.io.*;
import java.util.List;

@Size(max = 15)
public class MagicNumber
{
    private static final int s_foo = 400;
    private int m_foo = <warning descr="Magic number '-400'">-400</warning>;
    private static int s_foo2 = <warning descr="Magic number '400'">400</warning>;
    private final int m_foo2 = -(-(400));
    private static final List s_set = new ArrayList(400);
    private static final Dimension PREFERRED_SIZE = new Dimension(600, 400);

    public static void main(String[] args)
    {
        final List set = new ArrayList(400);
        set.toString();
    }

    public Dimension getScreenSize() {
        return new Dimension(2880, 1800);
    }

    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final MagicNumber magicNumber = (MagicNumber) o;

        if (m_foo != magicNumber.m_foo) return false;
        if (m_foo2 != magicNumber.m_foo2) return false;

        return true;
    }

    public int hashCode() {
        new Object() {
            private int i = <warning descr="Magic number '987'">987</warning>;
        };
        int result;
        result = m_foo;
        result = 29 * result + m_foo2;
        return result;
    }

    void foo() {
      final int value = 101 * 55;
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream(756);
    public static final long ONE_HOUR_OF_MILLISECONDS = 1000L * 60L * 60L;
    void waitOneHour() throws Exception {
      Thread.sleep(<warning descr="Magic number '1000L'">1000L</warning> * <warning descr="Magic number '60L'">60L</warning> * <warning descr="Magic number '60L'">60L</warning>);
    }
}
@interface Size {
  int max();
}
enum Planet {
    MERCURY (3.303e+23, 2.4397e6),
    VENUS   (4.869e+24, 6.0518e6),
    EARTH   (5.976e+24, 6.37814e6),
    MARS    (6.421e+23, 3.3972e6),
    JUPITER (1.9e+27,   7.1492e7),
    SATURN  (5.688e+26, 6.0268e7),
    URANUS  (8.686e+25, 2.5559e7),
    NEPTUNE (1.024e+26, 2.4746e7),
    PLUTO   (1.27e+22,  1.137e6);

    private final double mass;   // in kilograms
    private final double radius; // in meters
    Planet(double mass, double radius) {
        this.mass = mass;
        this.radius = radius;
    }
    public double mass()   { return mass; }
    public double radius() { return radius; }

    // universal gravitational constant  (m3 kg-1 s-2)
    public static final double G = 6.67300E-11;

    public double surfaceGravity() {
        return G * mass / (radius * radius);
    }
    public double surfaceWeight(double otherMass) {
        return otherMass * surfaceGravity();
    }
}