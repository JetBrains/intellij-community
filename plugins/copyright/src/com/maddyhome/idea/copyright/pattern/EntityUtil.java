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

package com.maddyhome.idea.copyright.pattern;

public class EntityUtil
{
    public static String encode(String text)
    {
        StringBuffer res = new StringBuffer(text.length());
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
        StringBuffer res = new StringBuffer(text.length());

        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            switch (ch)
            {
                case '&':
                    int semi = text.indexOf(';', i);
                    if (semi > i)
                    {
                        char newch = ch;
                        String entity = text.substring(i, semi + 1);
                        if (entity.equals("&#36;"))
                        {
                            newch = '$';
                        }
                        else if (entity.equals("&amp;"))
                        {
                            newch = '&';
                            i = semi;
                        }
                        if (newch != ch)
                        {
                            ch = newch;
                            i = semi;
                        }
                    }
                    res.append(ch);
                    break;
                default:
                    res.append(ch);
            }
        }

        return res.toString();
    }

    private EntityUtil()
    {
    }
}