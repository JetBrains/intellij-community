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

package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;
import com.intellij.util.PairProcessor;
import com.intellij.xml.util.XmlUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * This is the main part of the injection code. The component registers a language injector, the reference provider that
 * supplies completions for language-IDs and regular expression enum-values as well as the Quick Edit action.
 * <p/>
 * The injector obtains the static injection configuration for each XML tag, attribute or String literal and also
 * dynamically computes the prefix/suffix for the language fragment from binary expressions.
 * <p/>
 * It also tries to deal with the "glued token" problem by removing or adding whitespace to the prefix/suffix.
 */
public final class XmlLanguageInjector implements MultiHostInjector {
  private static final Comparator<TextRange> RANGE_COMPARATOR = new Comparator<TextRange>() {
    public int compare(final TextRange o1, final TextRange o2) {
      if (o1.intersects(o2)) return 0;
      return o1.getStartOffset() - o2.getStartOffset();
    }
  };

  private final Configuration myInjectionConfiguration;

  public XmlLanguageInjector(Configuration configuration) {
    myInjectionConfiguration = configuration;
  }

  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(XmlTag.class, XmlAttributeValue.class);
  }

  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull PsiElement host) {
    final TreeSet<TextRange> ranges = new TreeSet<TextRange>(RANGE_COMPARATOR);
    final PsiFile containingFile = host.getContainingFile();
    getInjectedLanguage(host, new PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>>() {
      public boolean process(final Language language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list) {
        for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : list) {
          if (ranges.contains(trinity.third.shiftRight(trinity.first.getTextRange().getStartOffset()))) return true;
        }
        for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : list) {
          final PsiLanguageInjectionHost host = trinity.first;
          if (host.getContainingFile() != containingFile) continue;
          final TextRange textRange = trinity.third;
          ranges.add(textRange.shiftRight(host.getTextRange().getStartOffset()));
        }
        registerInjection(language, list, containingFile, registrar);
        return true;
      }
    });
  }


  void getInjectedLanguage(final PsiElement place, final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor) {
    if (place instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)place;
      for (final XmlTagInjection injection : myInjectionConfiguration.getTagInjections()) {
        if (injection.isApplicable(xmlTag)) {
          final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
          if (language == null) continue;
          final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

          final Ref<Boolean> hasSubTags = Ref.create(Boolean.FALSE);
          final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result = new ArrayList<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>();

          xmlTag.acceptChildren(new PsiElementVisitor() {
            @Override
            public void visitElement(final PsiElement element) {
              if (element instanceof XmlText) {
                if (element.getTextLength() == 0) return;
                final List<TextRange> list = injection.getInjectedArea((XmlText)element);
                final InjectedLanguage l = InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);
                for (TextRange textRange : list) {
                  result.add(Trinity.create((PsiLanguageInjectionHost)element, l, textRange));
                }
              }
              else if (element instanceof XmlTag) {
                hasSubTags.set(Boolean.TRUE);
                if (injection.isApplyToSubTagTexts()) {
                  element.acceptChildren(this);
                }
              }
            }
          });
          if (!result.isEmpty()) {
            if (separateFiles) {
              for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
                processor.process(language, Collections.singletonList(trinity));
              }
            }
            else {
              for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
                trinity.first.putUserData(LanguageInjectorSupport.HAS_UNPARSABLE_FRAGMENTS, hasSubTags.get());
              }
              processor.process(language, result);
            }
          }
          if (injection.isTerminal()) {
            break;
          }
        }
      }
    }
    else if (place instanceof XmlAttributeValue) {
      final XmlAttributeValue value = (XmlAttributeValue)place;

      // Check that we don't inject anything into embedded (e.g. JavaScript) content:
      // XmlToken: "
      // JSEmbeddedContent
      // XmlToken "

      // Actually IDEA shouldn't ask for injected languages at all in this case.
      final PsiElement[] children = value.getChildren();
      if (children.length < 3 || !(children[1] instanceof XmlToken) ||
          ((XmlToken)children[1]).getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
        return;
      }

      for (XmlAttributeInjection injection : myInjectionConfiguration.getAttributeInjections()) {
        if (injection.isApplicable(value)) {
          final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
          if (language == null) continue;
          final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

          final List<TextRange> ranges = injection.getInjectedArea(value);
          if (ranges.isEmpty()) continue;
          final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result = new ArrayList<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>();
          final InjectedLanguage l = InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);
          for (TextRange textRange : ranges) {
            result.add(Trinity.create((PsiLanguageInjectionHost)value, l, textRange));
          }
          if (separateFiles) {
            for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
              processor.process(language, Collections.singletonList(trinity));
            }
          }
          else {
            processor.process(language, result);
          }
          if (injection.isTerminal()) {
            break;
          }
        }
      }
    }
  }

  private static void addPlaceSafe(MultiHostRegistrar registrar, String prefix, String suffix, PsiLanguageInjectionHost host, TextRange textRange) {
    registrar.addPlace(prefix, suffix, host, textRange);
  }

  private static String getUnescapedText(final PsiElement host, final String text) {
    if (host instanceof PsiLiteralExpression) {
      return StringUtil.unescapeStringCharacters(text);
    }
    else if (host instanceof XmlElement) {
      return XmlUtil.unescape(text);
    }
    else {
      return text;
    }
  }

  // Avoid sticking text and prefix/suffix together in a way that it would form a single token.
  // See http://www.jetbrains.net/jira/browse/IDEADEV-8302#action_111865
  // This code assumes that for the injected language a single space character is a token separator
  // that doesn't (significantly) change the semantics if added to the prefix/suffix
  //
  // NOTE: This does not work in all cases, such as string literals in JavaScript where a
  // space character isn't a token separator. See also comments in IDEA-8561
  private static void adjustPrefixAndSuffix(String text, StringBuilder prefix, StringBuilder suffix) {
    if (prefix.length() > 0) {
      if (!endsWithSpace(prefix) && !startsWithSpace(text)) {
        prefix.append(" ");
      }
      else if (endsWithSpace(prefix) && startsWithSpace(text)) {
        trim(prefix);
      }
    }
    if (suffix.length() > 0) {
      if (text.length() == 0) {
        // avoid to stick whitespace from prefix and suffix together
        trim(suffix);
      }
      else if (!startsWithSpace(suffix) && !endsWithSpace(text)) {
        suffix.insert(0, " ");
      }
      else if (startsWithSpace(suffix) && endsWithSpace(text)) {
        trim(suffix);
      }
    }
  }

  private static void trim(StringBuilder string) {
    while (startsWithSpace(string)) string.deleteCharAt(0);
    while (endsWithSpace(string)) string.deleteCharAt(string.length() - 1);
  }

  private static boolean startsWithSpace(CharSequence sequence) {
    final int length = sequence.length();
    return length > 0 && sequence.charAt(0) <= ' ';
  }

  private static boolean endsWithSpace(CharSequence sequence) {
    final int length = sequence.length();
    return length > 0 && sequence.charAt(length - 1) <= ' ';
  }

  static void registerInjection(Language language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list, PsiFile containingFile, MultiHostRegistrar registrar) {
    // if language isn't injected when length == 0, subsequent edits will not cause the language to be injected as well.
    // Maybe IDEA core is caching a bit too aggressively here?
    if (language == null/* && (pair.second.getLength() > 0*/) {
      return;
    }
    boolean injectionStarted = false;
    for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : list) {
      final PsiLanguageInjectionHost host = trinity.first;
      if (host.getContainingFile() != containingFile) continue;

      final TextRange textRange = trinity.third;
      final InjectedLanguage injectedLanguage = trinity.second;

      if (!injectionStarted) {
        registrar.startInjecting(language);
        injectionStarted = true;
      }
      if (injectedLanguage.isDynamic()) {
        // Only adjust prefix/suffix if it has been computed dynamically. Otherwise some other
        // useful cases may break. This system is far from perfect still...
        final StringBuilder prefix = new StringBuilder(injectedLanguage.getPrefix());
        final StringBuilder suffix = new StringBuilder(injectedLanguage.getSuffix());
        adjustPrefixAndSuffix(getUnescapedText(host, textRange.substring(host.getText())), prefix, suffix);

        addPlaceSafe(registrar, prefix.toString(), suffix.toString(), host, textRange);
      }
      else {
        addPlaceSafe(registrar, injectedLanguage.getPrefix(), injectedLanguage.getSuffix(), host, textRange);
      }
    }
    if (injectionStarted) {
      registrar.doneInjecting();
    }
  }

}
