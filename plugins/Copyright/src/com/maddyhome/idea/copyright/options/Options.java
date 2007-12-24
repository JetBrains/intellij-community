package com.maddyhome.idea.copyright.options;

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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jdom.Element;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 */
public class Options implements JDOMExternalizable, Cloneable
{
    public Options()
    {
        logger.info("Options()");
    }


    public LanguageOptions getOptions(String name)
    {
        String lang = FileTypeUtil.getInstance().getFileTypeNameByName(name);
        LanguageOptions res = options.get(lang);
        if (res == null)
        {
            res = LanguageOptionsFactory.createOptions(lang);
        }

        return res;
    }

    public LanguageOptions getTemplateOptions()
    {
        return getOptions(LANG_TEMPLATE);
    }

    public void setOptions(String name, LanguageOptions options)
    {
        String lang = FileTypeUtil.getInstance().getFileTypeNameByName(name);
        this.options.put(lang, options);
    }

    public void setTemplateOptions(LanguageOptions options)
    {
        setOptions(LANG_TEMPLATE, options);
    }

    public LanguageOptions getMergedOptions(String name)
    {
        try
        {
            LanguageOptions lang = getOptions(name).clone();
            LanguageOptions temp = getTemplateOptions().clone();
            switch (lang.getFileTypeOverride())
            {
                case LanguageOptions.USE_NONE:
                    lang.setNotice("");
                    break;
                case LanguageOptions.USE_TEMPLATE:
                    temp.setFileLocation(lang.getFileLocation());
                    temp.setFileTypeOverride(lang.getFileTypeOverride());
                    lang = temp;
                    break;
                case LanguageOptions.USE_TEXT:
                    lang.setNotice(temp.getNotice());
                    break;
                case LanguageOptions.USE_CUSTOM:
                    // No-op: lang is correct as-is
                    break;
            }

            return lang;
        }
        catch (CloneNotSupportedException e)
        {
            // This shouldn't happen
        }

        return null;
    }


    public void readExternal(Element element) throws InvalidDataException
    {
        logger.debug("readExternal()");
        List langs = element.getChildren("LanguageOptions");
        if (langs != null && langs.size() > 0)
        {
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < langs.size(); i++)
            {
                Element lang = (Element)langs.get(i);
                String name = lang.getAttributeValue("name");
                LanguageOptions opts = LanguageOptionsFactory.createOptions(name);
                opts.readExternal(lang);

                setOptions(name, opts);
            }
        }
        else
        {
            Element root = null;
            Element jOpts = element.getChild("JavaOptions");
            if (jOpts != null) // version 2.1.x
            {
                root = jOpts;
            }
            else // versions 0.0.1 - 2.0.x
            {
                Element child = element.getChild("option");
                if (child != null && child.getAttribute("name") != null)
                {
                    root = element;
                }
            }
            if (root != null)
            {
                String lname = StdFileTypes.JAVA.getName();
                LanguageOptions opts = LanguageOptionsFactory.createOptions(lname);
                opts.setFileTypeOverride(LanguageOptions.USE_CUSTOM);
                List children = root.getChildren("option");
                for (Object option : children)
                {
                    String name = ((Element)option).getAttributeValue("name");
                    String val = ((Element)option).getAttributeValue("value");
                    if ("body".equals(name))
                    {
                        opts.setNotice(val);
                    }
                    else if ("location".equals(name))
                    {
                        opts.setFileLocation(Integer.parseInt(val));
                    }
                }

                setOptions(lname, opts);
            }
        }

        logger.debug("options=" + this);
    }

    public void writeExternal(Element element) throws WriteExternalException
    {
        logger.debug("writeExternal()");

        for (String lang : options.keySet())
        {
            LanguageOptions opts = options.get(lang);

            Element elem = new Element("LanguageOptions");
            elem.setAttribute("name", lang);
            element.addContent(elem);
            opts.writeExternal(elem);
        }

        logger.debug("options=" + this);
    }

    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final Options options1 = (Options)o;

        return options.equals(options1.options);
    }

    public int hashCode()
    {
        int result;
        result = options.hashCode();
        return result;
    }

    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("Options");
        sb.append("{options=").append(options);
        sb.append('}');
        return sb.toString();
    }

    public Options clone() throws CloneNotSupportedException
    {
        Options res = (Options)super.clone();
        res.options = new TreeMap<String, LanguageOptions>();
        for (String lang : options.keySet())
        {
            LanguageOptions opts = options.get(lang);
            res.options.put(lang, opts.clone());
        }

        return res;
    }

    private Map<String, LanguageOptions> options = new TreeMap<String, LanguageOptions>();
    private static final String LANG_TEMPLATE = "__TEMPLATE__";

    private static Logger logger = Logger.getInstance(Options.class.getName());
}