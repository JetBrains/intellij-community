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

import com.intellij.lang.Language;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.StringPattern;
import com.intellij.patterns.compiler.PatternCompiler;
import com.intellij.patterns.compiler.PatternCompilerFactory;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.RegExp;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.jdom.CDATA;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Injection base class: Contains properties for language-id, prefix and suffix.
 */
public class BaseInjection implements Injection, PersistentStateComponent<Element> {

  public static final Key<BaseInjection> INJECTION_KEY = Key.create("INJECTION_KEY");

  @NotNull private final String mySupportId;

  private String myDisplayName = "";

  private String myInjectedLanguageId = "";
  private String myPrefix = "";
  private String mySuffix = "";

  @NonNls
  private String myValuePattern = "";
  private Pattern myCompiledValuePattern;
  private boolean mySingleFile;

  public BaseInjection(@NotNull final String id) {
    mySupportId = id;
  }

  public BaseInjection(@NotNull String supportId, @NotNull String injectedLanguageId, @NotNull String prefix, @NotNull String suffix, @NotNull InjectionPlace... places) {
    mySupportId = supportId;
    myInjectedLanguageId = injectedLanguageId;
    myPrefix = prefix;
    mySuffix = suffix;
    myPlaces = places;
  }

  @Nullable
  public Language getInjectedLanguage() {
    return InjectedLanguage.findLanguageById(myInjectedLanguageId);
  }

  @NotNull
  private InjectionPlace[] myPlaces = InjectionPlace.EMPTY_ARRAY;

  @NotNull
  public InjectionPlace[] getInjectionPlaces() {
    return myPlaces;
  }

  public void setInjectionPlaces(@NotNull InjectionPlace... places) {
    myPlaces = places;
  }

  @NotNull
  public String getSupportId() {
    return mySupportId;
  }

  @NotNull
  public String getInjectedLanguageId() {
    return myInjectedLanguageId;
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  public void setDisplayName(@NotNull String displayName) {
    myDisplayName = displayName;
  }

  public void setInjectedLanguageId(@NotNull String injectedLanguageId) {
    myInjectedLanguageId = injectedLanguageId;
  }

  @NotNull
  public String getPrefix() {
    return myPrefix;
  }

  public void setPrefix(@NotNull String prefix) {
    myPrefix = prefix;
  }

  @NotNull
  public String getSuffix() {
    return mySuffix;
  }

  public void setSuffix(@NotNull String suffix) {
    mySuffix = suffix;
  }

  @NotNull
  public List<TextRange> getInjectedArea(final PsiElement element) {
    final TextRange textRange = ElementManipulators.getValueTextRange(element);
    if (myCompiledValuePattern == null) {
      return Collections.singletonList(textRange);
    }
    else {
      final LiteralTextEscaper<? extends PsiLanguageInjectionHost> textEscaper =
              ((PsiLanguageInjectionHost)element).createLiteralTextEscaper();
      final StringBuilder sb = new StringBuilder();
      textEscaper.decode(textRange, sb);
      final List<TextRange> ranges = getMatchingRanges(myCompiledValuePattern.matcher(StringPattern.newBombedCharSequence(sb)), sb.length());
      return !ranges.isEmpty() ? ContainerUtil.map(ranges, s -> new TextRange(textEscaper.getOffsetInHost(s.getStartOffset(), textRange), textEscaper.getOffsetInHost(s.getEndOffset(), textRange))) : Collections.emptyList();
    }
  }

  public boolean isEnabled() {
    for (InjectionPlace place : myPlaces) {
      if (place.getElementPattern() != null && place.isEnabled()) return true;
    }
    return false;
  }
  
  public boolean acceptsPsiElement(final PsiElement element) {
    ProgressManager.checkCanceled();
    for (InjectionPlace place : myPlaces) {
      if (place.isEnabled() && place.getElementPattern() != null && place.getElementPattern().accepts(element)) {
        return true;
      }
    }
    return false;
  }

  public boolean intersectsWith(final BaseInjection template) {
    if (!Comparing.equal(getInjectedLanguageId(), template.getInjectedLanguageId())) return false;
    for (InjectionPlace other : template.getInjectionPlaces()) {
      if (ArrayUtil.contains(other, myPlaces)) return true;
    }
    return false;
  }

  public boolean sameLanguageParameters(final BaseInjection that) {
    if (!myInjectedLanguageId.equals(that.myInjectedLanguageId)) return false;
    if (!myPrefix.equals(that.myPrefix)) return false;
    if (!mySuffix.equals(that.mySuffix)) return false;
    if (!myValuePattern.equals(that.myValuePattern)) return false;
    if (mySingleFile != that.mySingleFile) return false;
    return true;
  }

  @SuppressWarnings({"unchecked"})
  public BaseInjection copy() {
    return new BaseInjection(mySupportId).copyFrom(this);
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BaseInjection)) return false;

    final BaseInjection that = (BaseInjection)o;

    if (!Comparing.equal(getDisplayName(), that.getDisplayName())) return false;
    if (!sameLanguageParameters(that)) return false;
    if (myPlaces.length != that.myPlaces.length) return false;
    for (int i = 0, len = myPlaces.length; i < len; i++) {
      if (myPlaces[i].isEnabled() != that.myPlaces[i].isEnabled()) {
        return false;
      }
    }
    // enabled flag is not counted this way:
    if (!Arrays.equals(myPlaces, that.myPlaces)) return false;
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

  public BaseInjection copyFrom(@NotNull BaseInjection other) {
    assert this != other;

    myInjectedLanguageId = other.getInjectedLanguageId();
    myPrefix = other.getPrefix();
    mySuffix = other.getSuffix();

    myDisplayName = other.getDisplayName();

    setValuePattern(other.getValuePattern());
    mySingleFile = other.mySingleFile;

    myPlaces = other.getInjectionPlaces().clone();
    return this;
  }

  public void loadState(@NotNull Element element) {
    final PatternCompiler<PsiElement> helper = getCompiler();
    myDisplayName = StringUtil.notNullize(element.getChildText("display-name"));
    myInjectedLanguageId = StringUtil.notNullize(element.getAttributeValue("language"));
    myPrefix = StringUtil.notNullize(element.getChildText("prefix"));
    mySuffix = StringUtil.notNullize(element.getChildText("suffix"));
    setValuePattern(element.getChildText("value-pattern"));
    mySingleFile = element.getChild("single-file") != null;
    readExternalImpl(element);
    final List<Element> placeElements = element.getChildren("place");
    myPlaces = InjectionPlace.ARRAY_FACTORY.create(placeElements.size());
    for (int i = 0, placeElementsSize = placeElements.size(); i < placeElementsSize; i++) {
      Element placeElement = placeElements.get(i);
      final boolean enabled = !Boolean.parseBoolean(placeElement.getAttributeValue("disabled"));
      final String text = placeElement.getText();
      myPlaces[i] = new InjectionPlace(helper.createElementPattern(text, getDisplayName()), enabled);
    }
    if (myPlaces.length == 0) {
      generatePlaces();
    }
  }


  public PatternCompiler<PsiElement> getCompiler() {
    return PatternCompilerFactory.getFactory().getPatternCompiler(InjectorUtils.getPatternClasses(getSupportId()));
  }

  public void generatePlaces() {
  }

  protected void readExternalImpl(Element e) {}

  public final Element getState() {
    final Element e = new Element("injection");
    e.setAttribute("language", myInjectedLanguageId);
    e.setAttribute("injector-id", mySupportId);
    e.addContent(new Element("display-name").setText(getDisplayName()));
    if (StringUtil.isNotEmpty(myPrefix)) {
      e.addContent(new Element("prefix").setText(myPrefix));
    }
    if (StringUtil.isNotEmpty(mySuffix)) {
      e.addContent(new Element("suffix").setText(mySuffix));
    }
    if (StringUtil.isNotEmpty(myValuePattern)) {
      e.addContent(new Element("value-pattern").setText(myValuePattern));
    }
    if (mySingleFile) {
      e.addContent(new Element("single-file"));
    }
    Arrays.sort(myPlaces, (o1, o2) -> Comparing.compare(o1.getText(), o2.getText()));
    for (InjectionPlace place : myPlaces) {
      final Element child = new Element("place").setContent(new CDATA(place.getText()));
      if (!place.isEnabled()) child.setAttribute("disabled", "true");
      e.addContent(child);
    }
    writeExternalImpl(e);
    return e;
  }

  protected void writeExternalImpl(Element e) {}

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
   * Determines if further injections should be examined if {@code isApplicable} has returned true.
   * <p/>
   * This is determined by the presence of a value-pattern: If none is present, the entry is considered
   * to be a terminal one.
   *
   * @return true to stop, false to continue
   */
  public boolean isTerminal() {
    return myCompiledValuePattern == null;
  }


  private static List<TextRange> getMatchingRanges(Matcher matcher, final int length) {
    final List<TextRange> list = new SmartList<>();
    int start = 0;
    while (start < length && matcher.find(start)) {
      final int groupCount = matcher.groupCount();
      if (groupCount == 0) {
        start = matcher.end();
      }
      else {
        for (int i=1; i<=groupCount; i++) {
          start = matcher.start(i);
          if (start == -1) continue;
          list.add(new TextRange(start, matcher.end(i)));
        }
        if (start >= matcher.end()) break;
        start = matcher.end();
      }
    }
    return list;
  }

  public void mergeOriginalPlacesFrom(final BaseInjection injection, final boolean enabled) {
    for (InjectionPlace place : injection.getInjectionPlaces()) {
      if (!ArrayUtil.contains(place, myPlaces)) {
        myPlaces = ArrayUtil.append(myPlaces, enabled || !place.isEnabled() ? place : place.enabled(false), InjectionPlace.ARRAY_FACTORY);
      }
    }
  }

  public void setPlaceEnabled(@Nullable final String text, final boolean enabled) {
    for (int i = 0; i < myPlaces.length; i++) {
      final InjectionPlace cur = myPlaces[i];
      if (text == null || Comparing.equal(text, cur.getText())) {
        if (cur.isEnabled() != enabled) {
          myPlaces[i] = cur.enabled(enabled);
        }
      }
    }
  }

  public boolean acceptForReference(PsiElement element) {
    return acceptsPsiElement(element);
  }

  @Override
  public String toString() {
    return getInjectedLanguageId()+ "->" +getDisplayName();
  }
}
