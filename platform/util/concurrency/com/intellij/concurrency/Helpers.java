// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

/*
 * Written by Martin Buchholz with assistance from members of JCP
 * JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

/** Shared implementation code for java.util.concurrent. */
class Helpers {
    /** Optimized form of: key + "=" + val */
    static String mapEntryToString(Object key, Object val) {
        final String k, v;
        final int klen, vlen;
        final char[] chars =
            new char[(klen = (k = objectToString(key)).length()) +
                     (vlen = (v = objectToString(val)).length()) + 1];
        k.getChars(0, klen, chars, 0);
        chars[klen] = '=';
        v.getChars(0, vlen, chars, klen + 1);
        return new String(chars);
    }

    private static String objectToString(Object x) {
        // Extreme compatibility with StringBuilder.append(null)
        String s;
        return (x == null || (s = x.toString()) == null) ? "null" : s;
    }
}
