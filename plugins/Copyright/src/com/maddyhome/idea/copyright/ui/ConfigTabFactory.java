package com.maddyhome.idea.copyright.ui;

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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;

public class ConfigTabFactory
{
    public static ConfigTab createConfigTab(FileType fileType, TemplateCommentPanel parentPanel)
    {
        // NOTE: If any change is made here you need to update LanguageOptionsFactory and UpdateCopyrightFactory too.
        ConfigTab res;
        if (fileType.equals(StdFileTypes.JAVA))
        {
            res = new JavaConfigTab(fileType, parentPanel);
        }
        else if (fileType.equals(StdFileTypes.XML))
        {
            res = new XmlConfigTab(fileType, parentPanel);
        }
        else if (fileType.equals(StdFileTypes.HTML))
        {
            res = new XmlConfigTab(fileType, parentPanel);
        }
        else if (fileType.equals(StdFileTypes.JSP))
        {
            res = new XmlConfigTab(fileType, parentPanel);
        }
        else
        {
            res = new BasicConfigTab(fileType, parentPanel);
        }

        res.setName(fileType.getName());

        return res;
    }

    private ConfigTabFactory()
    {
    }
}