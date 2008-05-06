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

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Injection base class: Contains properties for language-id, prefix and suffix.
 */
public abstract class BaseInjection<T extends BaseInjection, I extends PsiElement> implements Injection<I>, Cloneable, JDOMExternalizable {

  @NotNull
  private String myInjectedLanguageId = "";
  @NotNull
  private String myPrefix = "";
  @NotNull
  private String mySuffix = "";

  @NotNull
  public String getInjectedLanguageId() {
    return myInjectedLanguageId;
  }

  public void setInjectedLanguageId(@NotNull String injectedLanguageId) {
    myInjectedLanguageId = injectedLanguageId;
  }

  @NotNull
  public String getPrefix() {
    return myPrefix == null ? "" : myPrefix;
  }

  public void setPrefix(@NotNull String prefix) {
    myPrefix = prefix;
  }

  @NotNull
  public String getSuffix() {
    return mySuffix == null ? "" : mySuffix;
  }

  public void setSuffix(@NotNull String suffix) {
    mySuffix = suffix;
  }

  @SuppressWarnings({"unchecked"})
  public T copy() {
    try {
      return (T)clone();
    }
    catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final BaseInjection that = (BaseInjection)o;

    if (!myInjectedLanguageId.equals(that.myInjectedLanguageId)) return false;
    if (myPrefix != null ? !myPrefix.equals(that.myPrefix) : that.myPrefix != null) return false;
    if (mySuffix != null ? !mySuffix.equals(that.mySuffix) : that.mySuffix != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myInjectedLanguageId.hashCode();
    result = 31 * result + (myPrefix != null ? myPrefix.hashCode() : 0);
    result = 31 * result + (mySuffix != null ? mySuffix.hashCode() : 0);
    return result;
  }

  public void copyFrom(@NotNull T other) {
    assert this != other;

    myInjectedLanguageId = other.getInjectedLanguageId();
    myPrefix = other.getPrefix();
    mySuffix = other.getSuffix();
  }

  public void readExternal(Element element) throws InvalidDataException {
    final Element e = element.getChild(getClass().getSimpleName());
    if (e != null) {
      myInjectedLanguageId = JDOMExternalizer.readString(e, "LANGUAGE");
      myPrefix = JDOMExternalizer.readString(e, "PREFIX");
      mySuffix = JDOMExternalizer.readString(e, "SUFFIX");

      readExternalImpl(e);
    }
  }

  protected abstract void readExternalImpl(Element e) throws InvalidDataException;

  public final void writeExternal(Element element) throws WriteExternalException {
    final Element e = new Element(getClass().getSimpleName());
    element.addContent(e);

    JDOMExternalizer.write(e, "LANGUAGE", myInjectedLanguageId);
    JDOMExternalizer.write(e, "PREFIX", myPrefix);
    JDOMExternalizer.write(e, "SUFFIX", mySuffix);

    writeExternalImpl(e);
  }

  protected abstract void writeExternalImpl(Element e) throws WriteExternalException;
}
