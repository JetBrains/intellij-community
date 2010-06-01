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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.RegExp;
import org.intellij.plugins.intelliLang.PatternBasedInjectionHelper;
import org.jdom.CDATA;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Injection base class: Contains properties for language-id, prefix and suffix.
 */
public class BaseInjection implements Injection, PersistentStateComponent<Element> {

  public static final Key<BaseInjection> INJECTION_KEY = Key.create("INJECTION_KEY");

  @NotNull private final String mySupportId;
  private String myDisplayName;

  private String myInjectedLanguageId = "";
  private String myPrefix = "";
  private String mySuffix = "";

  @NonNls
  private String myValuePattern = "";
  private Pattern myCompiledValuePattern;
  private boolean mySingleFile;

  public BaseInjection(final String id) {
    mySupportId = id;
  }

  @NotNull
  private final List<InjectionPlace> myPlaces = new ArrayList<InjectionPlace>();

  @NotNull
  public List<InjectionPlace> getInjectionPlaces() {
    return myPlaces;
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

  public void setDisplayName(String displayName) {
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
      final List<TextRange> ranges = getMatchingRanges(myCompiledValuePattern.matcher(sb), sb.length());
      return ranges.size() > 0 ? ContainerUtil.map(ranges, new Function<TextRange, TextRange>() {
        public TextRange fun(TextRange s) {
          return new TextRange(textEscaper.getOffsetInHost(s.getStartOffset(), textRange), textEscaper.getOffsetInHost(s.getEndOffset(), textRange));
        }
      }) : Collections.<TextRange>emptyList();
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
      if (findPlaceByText(other.getText()) != null) return true;
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

  @Nullable
  public InjectionPlace findPlaceByText(final String text) {
    for (InjectionPlace cur : myPlaces) {
      if (Comparing.equal(text, cur.getText())) return cur;
    }
    return null;
  }

  @SuppressWarnings({"unchecked"})
  public BaseInjection copy() {
    return new BaseInjection(mySupportId).copyFrom(this);
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof BaseInjection)) return false;

    final BaseInjection that = (BaseInjection)o;

    if (!sameLanguageParameters(that)) return false;
    if (!myPlaces.equals(that.myPlaces)) return false;
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

    myPlaces.clear();
    myPlaces.addAll(other.getInjectionPlaces());
    return this;
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
      initializePlaces(false);
    }
    else {
      myDisplayName = StringUtil.notNullize(element.getChildText("display-name"));
      myInjectedLanguageId = StringUtil.notNullize(element.getAttributeValue("language"));
      myPrefix = StringUtil.notNullize(element.getChildText("prefix"));
      mySuffix = StringUtil.notNullize(element.getChildText("suffix"));
      setValuePattern(element.getChildText("value-pattern"));
      mySingleFile = element.getChild("single-file") != null;
      readExternalImpl(element);
      for (Element placeElement : (List<Element>)element.getChildren("place")) {
        final boolean enabled = !Boolean.parseBoolean(placeElement.getAttributeValue("disabled"));
        final String text = placeElement.getText();
        myPlaces.add(new InjectionPlace(text, null, enabled));
      }
    }
  }

  public void initializePlaces(final boolean compile) {
    if (myPlaces.isEmpty()) {
      for (String text : generatePlaces()) {
        myPlaces.add(new InjectionPlace(text, compile? PatternBasedInjectionHelper.createElementPattern(text, getDisplayName(), getSupportId()) : null, true));
      }
    }
    else if (compile) {
      boolean replace = false;
      final ArrayList<InjectionPlace> newPlaces = new ArrayList<InjectionPlace>();
      for (InjectionPlace place : myPlaces) {
        if (StringUtil.isNotEmpty(place.getText()) && place.getElementPattern() == null) {
          replace = true;
          newPlaces.add(new InjectionPlace(place.getText(), PatternBasedInjectionHelper.createElementPattern(place.getText(), getDisplayName(), getSupportId()), place.isEnabled()));
        }
        else {
          newPlaces.add(place);
        }
      }
      if (replace) {
        myPlaces.clear();
        myPlaces.addAll(newPlaces);
      }
    }
  }

  protected List<String> generatePlaces() {
    return Collections.emptyList();
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
    final ArrayList<InjectionPlace> places = new ArrayList<InjectionPlace>(myPlaces);
    Collections.sort(places, new Comparator<InjectionPlace>() {
      public int compare(final InjectionPlace o1, final InjectionPlace o2) {
        return Comparing.compare(o1.getText(), o2.getText());
      }
    });
    for (InjectionPlace place : places) {
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


  private static List<TextRange> getMatchingRanges(Matcher matcher, final int length) {
    final List<TextRange> list = new SmartList<TextRange>();
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
      if (findPlaceByText(place.getText()) == null) {
        if (enabled || !place.isEnabled()) {
          myPlaces.add(place);
        }
        else {
          myPlaces.add(new InjectionPlace(place.getText(), place.getElementPattern(), false));
        }
      }
    }
  }

  public void setPlaceEnabled(@Nullable final String text, final boolean enabled) {
    for (int i = 0; i < myPlaces.size(); i++) {
      final InjectionPlace cur = myPlaces.get(i);
      if (text == null || Comparing.equal(text, cur.getText())) {
        if (cur.isEnabled() != enabled) {
          myPlaces.set(i, new InjectionPlace(cur.getText(), cur.getElementPattern(), enabled));
        }
      }
    }
  }

  @Override
  public String toString() {
    return getInjectedLanguageId()+ "->" +getDisplayName();
  }
}
