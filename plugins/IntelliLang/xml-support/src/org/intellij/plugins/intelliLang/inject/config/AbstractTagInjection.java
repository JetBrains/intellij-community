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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import org.intellij.plugins.intelliLang.inject.xml.XmlLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.util.StringMatcher;
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Base class for XML-related injections (XML tags and attributes).
 * Contains the tag's local name and namespace-uri, an optional value-pattern
 * and an optional XPath expression (only valid if XPathView is installed) and
 * the appropriate logic to determine if a tag matches those properties.
 *
 * @see XPathSupportProxy
 */
public abstract class AbstractTagInjection extends BaseInjection {

  private static final Logger LOG = Logger.getInstance(AbstractTagInjection.class);

  private @NotNull @NlsSafe StringMatcher myTagName = StringMatcher.ANY;

  private @NotNull @NlsSafe Set<String> myTagNamespace = Collections.emptySet();
  private @NotNull @NlsSafe String myXPathCondition = "";

  private volatile Condition<XmlElement> myCompiledXPathCondition;
  private boolean myApplyToSubTags;

  public AbstractTagInjection() {
    super(XmlLanguageInjectionSupport.XML_SUPPORT_ID);
  }

  public @NotNull String getTagName() {
    return myTagName.getPattern();
  }

  public void setTagName(@NotNull @NlsSafe String tagName) {
    myTagName = StringMatcher.create(tagName);
  }

  @Override
  public boolean acceptsPsiElement(final PsiElement element) {
    return super.acceptsPsiElement(element) &&
           (!(element instanceof XmlElement) || matchXPath((XmlElement)element));
  }

  public @NotNull @NlsSafe String getTagNamespace() {
    return StringUtil.join(myTagNamespace, "|");
  }

  public void setTagNamespace(@NotNull @NonNls String tagNamespace) {
    myTagNamespace = new TreeSet<>(StringUtil.split(tagNamespace, "|"));
  }

  public @NotNull String getXPathCondition() {
    return myXPathCondition;
  }

  public void setXPathCondition(@Nullable String condition) {
    myXPathCondition = StringUtil.notNullize(condition);
    myCompiledXPathCondition = null;
  }

  private Condition<XmlElement> compileXPath() {
    if (StringUtil.isEmptyOrSpaces(myXPathCondition)) return Conditions.alwaysTrue();

    try {
      XPathSupportProxy xPathSupport = XPathSupportProxy.getInstance();
      if (xPathSupport != null) {
        XPath path = xPathSupport.createXPath(myXPathCondition);
        return context -> {
          try {
            return path.booleanValueOf(context);
          }
          catch (JaxenException e) {
            LOG.warn(e);
            myCompiledXPathCondition = Conditions.alwaysFalse();
            return false;
          }
        };
      }
    }
    catch (JaxenException e) {
      LOG.warn("Invalid XPath expression", e);
    }
    return Conditions.alwaysFalse();
  }

  @SuppressWarnings({"RedundantIfStatement"})
  protected boolean matches(@Nullable XmlTag tag) {
    if (tag == null) {
      return false;
    }
    if (!myTagName.matches(tag.getLocalName())) {
      return false;
    }
    if (!myTagNamespace.contains(tag.getNamespace())) {
      return false;
    }
    return true;
  }

  @Override
  public abstract AbstractTagInjection copy();

  @Override
  public AbstractTagInjection copyFrom(@NotNull BaseInjection o) {
    super.copyFrom(o);
    if (o instanceof AbstractTagInjection other) {
      myTagName = other.myTagName;
      myTagNamespace = other.myTagNamespace;
      setXPathCondition(other.getXPathCondition());

      setApplyToSubTags(other.isApplyToSubTags());
    }
    return this;
  }

  @Override
  protected void readExternalImpl(Element e) {
    setXPathCondition(e.getChildText("xpath-condition"));
    myApplyToSubTags = e.getChild("apply-to-subtags") != null;
  }

  @Override
  protected void writeExternalImpl(Element e) {
    if (StringUtil.isNotEmpty(myXPathCondition)) {
      e.addContent(new Element("xpath-condition").setText(myXPathCondition));
    }
    if (myApplyToSubTags) {
      e.addContent(new Element("apply-to-subtags"));
    }
  }

  @Override
  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final AbstractTagInjection that = (AbstractTagInjection)o;

    if (!myTagName.equals(that.myTagName)) return false;
    if (!myTagNamespace.equals(that.myTagNamespace)) return false;
    if (!myXPathCondition.equals(that.myXPathCondition)) return false;

    if (myApplyToSubTags != that.myApplyToSubTags) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myTagName.hashCode();
    result = 31 * result + myTagNamespace.hashCode();
    result = 31 * result + myXPathCondition.hashCode();

    result = 31 * result + (myApplyToSubTags ? 1 : 0);
    return result;
  }

  boolean matchXPath(XmlElement context) {
    Condition<XmlElement> compiled = myCompiledXPathCondition;
    if (compiled == null) {
      myCompiledXPathCondition = compiled = compileXPath();
    }
    return compiled.value(context);
  }

  public boolean isApplyToSubTags() {
    return myApplyToSubTags;
  }

  public void setApplyToSubTags(final boolean applyToSubTagTexts) {
    myApplyToSubTags = applyToSubTagTexts;
  }

  @Override
  public boolean acceptForReference(PsiElement element) {
    if (element instanceof XmlAttributeValue) {
      PsiElement parent = element.getParent();
      return parent instanceof XmlAttribute && acceptsPsiElement(parent);
    }
    else return element instanceof XmlTag && acceptsPsiElement(element);
  }
}
