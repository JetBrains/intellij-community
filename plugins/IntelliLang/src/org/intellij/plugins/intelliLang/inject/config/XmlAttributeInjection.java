/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.Function;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import org.intellij.plugins.intelliLang.util.StringMatcher;

public class XmlAttributeInjection extends AbstractTagInjection<XmlAttributeInjection, XmlAttributeValue>  {

    @NotNull @NonNls
    private StringMatcher myAttributeNameMatcher = StringMatcher.NONE;
    @NotNull @NonNls
    private String myAttributeNamespace = "";

    @NotNull
    public String getAttributeName() {
        return myAttributeNameMatcher.getPattern();
    }

    public void setAttributeName(@NotNull String attributeName) {
        myAttributeNameMatcher = StringMatcher.create(attributeName);
    }

    @NotNull
    public String getAttributeNamespace() {
        return myAttributeNamespace;
    }

    public void setAttributeNamespace(@NotNull String attributeNamespace) {
        myAttributeNamespace = attributeNamespace;
    }

    public boolean isApplicable(@NotNull XmlAttributeValue value) {
        final PsiElement element = value.getParent();
        return element instanceof XmlAttribute && matches((XmlAttribute)element);
    }

    public String getDisplayName() {
        final String tag = getTagName();
        final String attributeName = getAttributeName();
        if (!attributeName.equals(StringMatcher.NONE.getPattern())) {
            if (tag.length() > 0) {
                return tag + "/@" + (attributeName.length() > 0 ? attributeName : "*");
            } else {
                return "*/@" + (attributeName.length() > 0 ? attributeName : "*");
            }
        }
        return attributeName;
    }

    private boolean matches(@NotNull XmlAttribute attr) {
        // mind IDEA-5206
        final boolean b = myAttributeNameMatcher.matches(attr.getLocalName()) &&
                (attr.getName().indexOf(':') == -1 || myAttributeNamespace.equals(attr.getNamespace())) &&
                matches(attr.getParent());

        return b && matchXPath(attr);
    }

    @NotNull
    public List<TextRange> getInjectedArea(final XmlAttributeValue element) {
        if (myCompiledValuePattern == null) {
            return Collections.singletonList(TextRange.from(1, element.getTextLength() - 2));
        } else {
            final XmlAttribute attr = (XmlAttribute)element.getParent();
            final List<TextRange> ranges = getMatchingRanges(myCompiledValuePattern.matcher(attr.getDisplayValue()), 1);
            return ranges.size() > 0 ? ContainerUtil.map(ranges, new Function<TextRange, TextRange>() {
                public TextRange fun(TextRange s) {
                    return new TextRange(attr.displayToPhysical(s.getStartOffset()), attr.displayToPhysical(s.getEndOffset()));
                }
            }) : Collections.<TextRange>emptyList();
        }
    }

    public void copyFrom(@NotNull XmlAttributeInjection other) {
        super.copyFrom(other);
        setAttributeName(other.getAttributeName());
        setAttributeNamespace(other.getAttributeNamespace());
    }

    protected void readExternalImpl(Element e) {
        super.readExternalImpl(e);
        setAttributeName(JDOMExternalizer.readString(e, "ATT_NAME"));
        setAttributeNamespace(JDOMExternalizer.readString(e, "ATT_NAMESPACE"));
    }

    protected void writeExternalImpl(Element e) {
        super.writeExternalImpl(e);
        JDOMExternalizer.write(e, "ATT_NAME", myAttributeNameMatcher.getPattern());
        JDOMExternalizer.write(e, "ATT_NAMESPACE", myAttributeNamespace);
    }

    @SuppressWarnings({ "RedundantIfStatement" })
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        final XmlAttributeInjection that = (XmlAttributeInjection)o;

        if (!myAttributeNameMatcher.getPattern().equals(that.myAttributeNameMatcher.getPattern())) return false;
        if (!myAttributeNamespace.equals(that.myAttributeNamespace)) return false;

        return true;
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + myAttributeNameMatcher.getPattern().hashCode();
        result = 31 * result + myAttributeNamespace.hashCode();
        return result;
    }
}
