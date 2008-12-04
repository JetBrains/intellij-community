/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.maddyhome.idea.copyright.options;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class LanguageOptions implements JDOMExternalizable, Cloneable
{
    public static final int USE_NONE = 1;
    public static final int USE_TEMPLATE = 2;
    public static final int USE_TEXT = 3;
    

    public LanguageOptions()
    {
        setDefaults();
    }

    public void setDefaults()
    {
        templateOptions = new TemplateOptions();
        templateOptions.setDefaults();

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

    /*public String getNotice()
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
    }*/

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
        return !(templateOptions != null ? !templateOptions.equals(that.templateOptions) :
            that.templateOptions != null);
    }

    public int hashCode()
    {
        int result;
        result = (templateOptions != null ? templateOptions.hashCode() : 0);
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
        if (fileTypeOverride == USE_TEXT )
        {
            templateOptions.validate();
        }
    }

    public TemplateOptions templateOptions;
    public int fileTypeOverride;
    public boolean relativeBefore;
    public boolean addBlankAfter;
    public int fileLocation;
    public boolean useAlternate;
}