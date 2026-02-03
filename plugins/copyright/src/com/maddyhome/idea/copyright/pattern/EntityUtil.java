// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.pattern;

public final class EntityUtil
{
    public static String encode(String text)
    {
        StringBuilder res = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            if (ch == '&')
            {
                res.append("&amp;");
            }
            else if (ch == '$')
            {
                res.append("&#36;");
            }
            else
            {
                res.append(ch);
            }
        }

        return res.toString();
    }

    public static String decode(String text)
    {
        StringBuilder res = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            if (ch == '&') {
              int semi = text.indexOf(';', i);
              if (semi > i) {
                char newch = '&';
                String entity = text.substring(i, semi + 1);
                if (entity.equals("&#36;")) {
                  newch = '$';
                }
                else if (entity.equals("&amp;")) {
                  i = semi;
                }
                if (newch != ch) {
                  ch = newch;
                  i = semi;
                }
              }
            }
            res.append(ch);
        }

        return res.toString();
    }

    private EntityUtil()
    {
    }
}