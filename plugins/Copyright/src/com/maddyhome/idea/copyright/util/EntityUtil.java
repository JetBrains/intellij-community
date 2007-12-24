package com.maddyhome.idea.copyright.util;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

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