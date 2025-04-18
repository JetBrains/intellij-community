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
package org.intellij.plugins.intelliLang.inject.xml;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.*;
import com.intellij.util.PairProcessor;
import com.intellij.util.PatternValuesIndex;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.InjectorUtils.InjectionInfo;
import org.intellij.plugins.intelliLang.inject.config.AbstractTagInjection;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * This is the main part of the injection code. The component registers a language injector, the reference provider that
 * supplies completions for language-IDs and regular expression enum-values as well as the Quick Edit action.
 * <p/>
 * The injector obtains the static injection configuration for each XML tag, attribute or String literal and also
 * dynamically computes the prefix/suffix for the language fragment from binary expressions.
 * <p/>
 * It also tries to deal with the "glued token" problem by removing or adding whitespace to the prefix/suffix.
 */
final class XmlLanguageInjector implements MultiHostInjector {
  record XmlIndex(long modCount, @Nullable Pattern pattern, @NotNull Collection<String> strings) {}
  private volatile XmlIndex myXmlIndex;
  private final Project myProject;

  @SuppressWarnings("PublicConstructorInNonPublicClass")
  public XmlLanguageInjector(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(XmlTag.class, XmlAttributeValue.class);
  }

  @Override
  public void getLanguagesToInject(final @NotNull MultiHostRegistrar registrar, @NotNull PsiElement host) {
    final XmlElement xmlElement = (XmlElement) host;
    if (!isInIndex(xmlElement)) return;
    final TreeSet<TextRange> ranges = new TreeSet<>(InjectorUtils.RANGE_COMPARATOR);
    final PsiFile containingFile = xmlElement.getContainingFile();
    final Ref<Boolean> unparsableRef = Ref.create();
    getInjectedLanguage(xmlElement, unparsableRef, (language, list) -> {
      for (InjectionInfo trinity : list) {
        if (ranges.contains(trinity.range().shiftRight(trinity.host().getTextRange().getStartOffset()))) return true;
      }
      for (InjectionInfo trinity : list) {
        if (trinity.host().getContainingFile() != containingFile) continue;
        final TextRange textRange = trinity.range();
        ranges.add(textRange.shiftRight(trinity.host().getTextRange().getStartOffset()));
      }
      InjectorUtils.registerInjection(language, containingFile, list, registrar, it -> {
        InjectorUtils.registerSupport(it, InjectorUtils.findNotNullInjectionSupport(XmlLanguageInjectionSupport.XML_SUPPORT_ID), true);
        if (Boolean.TRUE.equals(unparsableRef.get())) {
          it.frankensteinInjection(true);
        }
      });
      return true;
    });
  }

  void getInjectedLanguage(final PsiElement place,
                           final Ref<? super Boolean> unparsableRef,
                           final PairProcessor<? super Language, ? super List<InjectionInfo>> processor) {
    if (place instanceof XmlTag xmlTag) {

      List<BaseInjection> injections = getConfiguration().getInjections(XmlLanguageInjectionSupport.XML_SUPPORT_ID);
      for (int i = 0, injectionsSize = injections.size(); i < injectionsSize; i++) {
        final BaseInjection injection = injections.get(i);
        if (injection.acceptsPsiElement(xmlTag)) {
          final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
          if (language == null) continue;
          final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

          final List<InjectionInfo> result = new ArrayList<>();

          xmlTag.acceptChildren(new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
              if (element instanceof XmlText) {
                if (!(element instanceof PsiLanguageInjectionHost) || element.getTextLength() == 0) return;

                if (!injection.shouldBeIgnored(element)) {
                  List<TextRange> list = injection.getInjectedArea(element);
                  InjectedLanguage l =
                    InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);
                  for (TextRange textRange : list) {
                    result.add(new InjectionInfo((PsiLanguageInjectionHost)element, l, textRange));
                  }
                }
              }
              else if (element instanceof XmlTag) {
                if (!separateFiles) unparsableRef.set(Boolean.TRUE);
                if (injection instanceof AbstractTagInjection && ((AbstractTagInjection)injection).isApplyToSubTags()) {
                  element.acceptChildren(this);
                }
              }
            }
          });
          if (!result.isEmpty()) {
            if (separateFiles) {
              for (InjectionInfo trinity : result) {
                processor.process(language, Collections.singletonList(trinity));
              }
            }
            else {
              processor.process(language, result);
            }
          }
          if (injection.isTerminal()) {
            break;
          }
        }
      }
    }
    else if (place instanceof XmlAttributeValue value && place.getParent() instanceof XmlAttribute attribute) {
      //if (value == null) return;
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

      List<BaseInjection> injections = getConfiguration().getInjections(XmlLanguageInjectionSupport.XML_SUPPORT_ID);
      for (int i = 0, size = injections.size(); i < size; i++) {
        BaseInjection injection = injections.get(i);
        if (injection.acceptsPsiElement(attribute) && !injection.shouldBeIgnored(value)) {
          final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
          if (language == null) continue;
          final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

          final List<TextRange> ranges = injection.getInjectedArea(value);
          if (ranges.isEmpty()) continue;
          final List<InjectionInfo> result = new ArrayList<>();
          final InjectedLanguage l =
            InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);
          for (TextRange textRange : ranges) {
            result.add(new InjectionInfo((PsiLanguageInjectionHost)value, l, textRange));
          }
          if (separateFiles) {
            for (InjectionInfo info : result) {
              processor.process(language, Collections.singletonList(info));
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

  private Configuration getConfiguration() {
    return Configuration.getProjectInstance(myProject);
  }

  // NOTE: local name of an XML entity or attribute value should match at least one string in the index
  private boolean isInIndex(XmlElement xmlElement) {
    final XmlIndex index = getXmlAnnotatedElementsValue();
    if (xmlElement instanceof XmlAttributeValue) xmlElement = (XmlElement)xmlElement.getParent();
    final XmlTag tag;
    if (xmlElement instanceof XmlAttribute attribute) {
      if (areThereInjectionsWithText(attribute.getLocalName(), index)) return true;
      if (areThereInjectionsWithText(attribute.getValue(), index)) return true;
      //if (areThereInjectionsWithText(attribute.getNamespace(), index)) return false;
      tag = attribute.getParent();
      if (tag == null) return false;
    }
    else if (xmlElement instanceof XmlTag) {
      tag = (XmlTag)xmlElement;
    }
    else {
      return false;
    }
    if (areThereInjectionsWithText(tag.getLocalName(), index)) return true;
    //if (areThereInjectionsWithText(tag.getNamespace(), index)) return false;
    return false;
  }

  private static boolean areThereInjectionsWithText(final String text, XmlIndex index) {
    if (text == null) return false;
    if (index.strings.contains(text)) return true;
    Pattern pattern = index.pattern;
    return pattern != null && pattern.matcher(text).matches();
  }

  private XmlIndex getXmlAnnotatedElementsValue() {
    Configuration configuration = getConfiguration();

    XmlIndex index = myXmlIndex;
    if (index == null || configuration.getModificationCount() != index.modCount) {
      final Map<ElementPattern<?>, BaseInjection> map = new HashMap<>();
      for (BaseInjection injection : configuration.getInjections(XmlLanguageInjectionSupport.XML_SUPPORT_ID)) {
        for (InjectionPlace place : injection.getInjectionPlaces()) {
          if (!place.isEnabled() || place.getElementPattern() == null) continue;
          map.put(place.getElementPattern(), injection);
        }
      }
      final Collection<String> stringSet = PatternValuesIndex.buildStringIndex(map.keySet());
      index = new XmlIndex(configuration.getModificationCount(), buildPattern(stringSet), stringSet);
      myXmlIndex = index;
    }
    return index;
  }

  private static @Nullable Pattern buildPattern(Collection<String> stringSet) {
    final StringBuilder sb = new StringBuilder();
    for (String s : stringSet) {
      if (!InjectorUtils.isRegexp(s)) continue;
      if (!sb.isEmpty()) sb.append('|');
      sb.append("(?:").append(s).append(")");
    }
    return sb.isEmpty() ? null : Pattern.compile(sb.toString());
  }
}
