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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.SmartList;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.RegExp;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Injection base class: Contains properties for language-id, prefix and suffix.
 */
public abstract class BaseInjection<T extends BaseInjection, I extends PsiElement> implements Injection<I>, Cloneable,
                                                                                              PersistentStateComponent<Element> {

  @NotNull
  private String myInjectedLanguageId = "";
  @NotNull
  private String myPrefix = "";
  @NotNull
  private String mySuffix = "";

  @NotNull @NonNls
  private String myValuePattern = "";
  private Pattern myCompiledValuePattern;
  private boolean mySingleFile;

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

  @NotNull
  public List<TextRange> getInjectedArea(final I element) {
    final TextRange textRange = ElementManipulators.getValueTextRange(element);
    if (myCompiledValuePattern == null) {
      return Collections.singletonList(textRange);
    }
    else {
      final LiteralTextEscaper<? extends PsiLanguageInjectionHost> textEscaper =
              ((PsiLanguageInjectionHost)element).createLiteralTextEscaper();
      final StringBuilder sb = new StringBuilder();
      textEscaper.decode(textRange, sb);
      final List<TextRange> ranges = getMatchingRanges(myCompiledValuePattern.matcher(sb.toString()));
      return ranges.size() > 0 ? ContainerUtil.map(ranges, new Function<TextRange, TextRange>() {
        public TextRange fun(TextRange s) {
          return new TextRange(textEscaper.getOffsetInHost(s.getStartOffset(), textRange), textEscaper.getOffsetInHost(s.getEndOffset(), textRange));
        }
      }) : Collections.<TextRange>emptyList();
    }
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
    if (!myPrefix.equals(that.myPrefix)) return false;
    if (!mySuffix.equals(that.mySuffix)) return false;
    if (!myValuePattern.equals(that.myValuePattern)) return false;
    if (mySingleFile != that.mySingleFile) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myInjectedLanguageId.hashCode();
    result = 31 * result + myPrefix.hashCode();
    result = 31 * result + mySuffix.hashCode();
    result = 31 * result + myValuePattern.hashCode();
    return result;
  }

  public void copyFrom(@NotNull T other) {
    assert this != other;

    myInjectedLanguageId = other.getInjectedLanguageId();
    myPrefix = other.getPrefix();
    mySuffix = other.getSuffix();

    setValuePattern(other.getValuePattern());
    mySingleFile = other.mySingleFile;
  }

  public void loadState(Element element) {
    final Element e = element.getChild(getClass().getSimpleName());
    if (e != null) {
      myInjectedLanguageId = JDOMExternalizer.readString(e, "LANGUAGE");
      myPrefix = JDOMExternalizer.readString(e, "PREFIX");
      mySuffix = JDOMExternalizer.readString(e, "SUFFIX");
      setValuePattern(JDOMExternalizer.readString(e, "VALUE_PATTERN"));
      mySingleFile = JDOMExternalizer.readBoolean(e, "SINGLE_FILE");
      readExternalImpl(e);
    }
  }

  protected abstract void readExternalImpl(Element e);

  public final Element getState() {
    final Element e = new Element(getClass().getSimpleName());

    JDOMExternalizer.write(e, "LANGUAGE", myInjectedLanguageId);
    JDOMExternalizer.write(e, "PREFIX", myPrefix);
    JDOMExternalizer.write(e, "SUFFIX", mySuffix);
    JDOMExternalizer.write(e, "VALUE_PATTERN", myValuePattern);
    JDOMExternalizer.write(e, "SINGLE_FILE", mySingleFile);

    writeExternalImpl(e);
    return e;
  }

  protected abstract void writeExternalImpl(Element e);

  @NotNull
  public String getValuePattern() {
    return myValuePattern;
  }

  public void setValuePattern(@RegExp @Nullable String pattern) {
    try {
      if (pattern != null && pattern.length() > 0) {
        myValuePattern = pattern;
        myCompiledValuePattern = Pattern.compile(pattern, Pattern.DOTALL);
      }
      else {
        myValuePattern = "";
        myCompiledValuePattern = null;
      }
    }
    catch (Exception e1) {
      myCompiledValuePattern = null;
      Logger.getInstance(getClass().getName()).info("Invalid pattern", e1);
    }
  }

  public boolean isSingleFile() {
    return mySingleFile;
  }

  public void setSingleFile(final boolean singleFile) {
    mySingleFile = singleFile;
  }

  /**
   * Determines if further injections should be examined if <code>isApplicable</code> has returned true.
   * <p/>
   * This is determined by the presence of a value-pattern: If none is present, the entry is considered
   * to be a terminal one.
   *
   * @return true to stop, false to continue
   */
  public boolean isTerminal() {
    return myCompiledValuePattern == null;
  }


  private static List<TextRange> getMatchingRanges(Matcher matcher) {
    final List<TextRange> list = new SmartList<TextRange>();
    int start = 0;
    while (matcher.find(start)) {
      final String group = matcher.group(1);
      if (group != null) {
        start = matcher.start(1);
        final int length = group.length();
        list.add(TextRange.from(start, length));
        start += length;
      }
      else {
        break;
      }
    }
    return list;
  }

}
