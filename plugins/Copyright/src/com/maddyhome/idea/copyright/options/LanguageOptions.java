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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.maddyhome.idea.copyright.util.EntityUtil;
import com.maddyhome.idea.copyright.util.VelocityHelper;
import org.jdom.Element;

public class LanguageOptions implements JDOMExternalizable, Cloneable
{
    public static final int USE_NONE = 1;
    public static final int USE_TEMPLATE = 2;
    public static final int USE_TEXT = 3;
    public static final int USE_CUSTOM = 4;

    public LanguageOptions()
    {
        setDefaults();
    }

    public void setDefaults()
    {
        templateOptions = new TemplateOptions();
        templateOptions.setDefaults();
        notice = EntityUtil.encode("Copyright (c) $today.year, Your Corporation. All Rights Reserved.");
        keyword = EntityUtil.encode("Copyright");
        fileTypeOverride = USE_TEMPLATE;
        relativeBefore = true;
        addBlankAfter = true;
        fileLocation = 1;
        useAlternate = false;
    }

    public TemplateOptions getTemplateOptions()
    {
        return templateOptions;
    }

    public void setTemplateOptions(TemplateOptions templateOptions)
    {
        this.templateOptions = templateOptions;
    }

    public String getNotice()
    {
        return EntityUtil.decode(notice);
    }

    public void setNotice(String notice)
    {
        this.notice = EntityUtil.encode(notice);
    }

    public String getKeyword()
    {
        return EntityUtil.decode(keyword);
    }

    public void setKeyword(String keyword)
    {
        this.keyword = EntityUtil.encode(keyword);
    }

    public int getFileTypeOverride()
    {
        return fileTypeOverride;
    }

    public void setFileTypeOverride(int fileTypeOverride)
    {
        this.fileTypeOverride = fileTypeOverride;
    }

    public boolean isRelativeBefore()
    {
        return relativeBefore;
    }

    public void setRelativeBefore(boolean relativeBefore)
    {
        this.relativeBefore = relativeBefore;
    }

    public boolean isAddBlankAfter()
    {
        return addBlankAfter;
    }

    public void setAddBlankAfter(boolean addBlankAfter)
    {
        this.addBlankAfter = addBlankAfter;
    }

    public int getFileLocation()
    {
        return fileLocation;
    }

    public void setFileLocation(int fileLocation)
    {
        this.fileLocation = fileLocation;
    }

    public boolean isUseAlternate()
    {
        return useAlternate;
    }

    public void setUseAlternate(boolean useAlternate)
    {
        this.useAlternate = useAlternate;
    }

    public void readExternal(Element element) throws InvalidDataException
    {
        DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException
    {
        DefaultJDOMExternalizer.writeExternal(this, element);
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

        final LanguageOptions that = (LanguageOptions)o;

        if (addBlankAfter != that.addBlankAfter)
        {
            return false;
        }
        if (fileLocation != that.fileLocation)
        {
            return false;
        }
        if (fileTypeOverride != that.fileTypeOverride)
        {
            return false;
        }
        if (relativeBefore != that.relativeBefore)
        {
            return false;
        }
        if (useAlternate != that.useAlternate)
        {
            return false;
        }
        if (keyword != null ? !keyword.equals(that.keyword) : that.keyword != null)
        {
            return false;
        }
        if (notice != null ? !notice.equals(that.notice) : that.notice != null)
        {
            return false;
        }
        return !(templateOptions != null ? !templateOptions.equals(that.templateOptions) :
            that.templateOptions != null);
    }

    public int hashCode()
    {
        int result;
        result = (templateOptions != null ? templateOptions.hashCode() : 0);
        result = 29 * result + (notice != null ? notice.hashCode() : 0);
        result = 29 * result + (keyword != null ? keyword.hashCode() : 0);
        result = 29 * result + fileTypeOverride;
        result = 29 * result + (relativeBefore ? 1 : 0);
        result = 29 * result + (addBlankAfter ? 1 : 0);
        result = 29 * result + fileLocation;
        result = 29 * result + (useAlternate ? 1 : 0);
        return result;
    }

    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("LanguageOptions");
        sb.append("{templateOptions=").append(templateOptions);
        sb.append(", notice='").append(notice).append('\'');
        sb.append(", keyword='").append(keyword).append('\'');
        sb.append(", fileTypeOverride=").append(fileTypeOverride);
        sb.append(", relativeBefore=").append(relativeBefore);
        sb.append(", addBlankAfter=").append(addBlankAfter);
        sb.append(", fileLocation=").append(fileLocation);
        sb.append(", useAlternate=").append(useAlternate);
        sb.append('}');
        return sb.toString();
    }

    public LanguageOptions clone() throws CloneNotSupportedException
    {
        LanguageOptions res = (LanguageOptions)super.clone();
        res.templateOptions = templateOptions.clone();

        return res;
    }

    public void validate() throws ConfigurationException
    {
        if (fileTypeOverride == USE_TEXT || fileTypeOverride == USE_CUSTOM)
        {
            templateOptions.validate();
        }
        if (fileTypeOverride == USE_CUSTOM && (keyword == null || keyword.length() == 0))
        {
            throw new ConfigurationException("Copyright keyword is required.");
        }
        if (fileTypeOverride == USE_CUSTOM && (notice == null || notice.length() == 0))
        {
            throw new ConfigurationException("Copyright text is required.");
        }
        if (fileTypeOverride == USE_CUSTOM)
        {
            try
            {
                VelocityHelper.verify(notice);
            }
            catch (Exception e)
            {
                throw new ConfigurationException("Copyright notice error:\n" + e.getMessage());
            }
        }
    }

    public TemplateOptions templateOptions;
    public String notice;
    public String keyword;
    public int fileTypeOverride;
    public boolean relativeBefore;
    public boolean addBlankAfter;
    public int fileLocation;
    public boolean useAlternate;
}