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
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.Injection;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.ContextComputationProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This is the main part of the injection code. The component registers a language injector, the reference provider that
 * supplies completions for language-IDs and regular expression enum-values as well as the Quick Edit action.
 * <p/>
 * The injector obtains the static injection configuration for each XML tag, attribute or String literal and also
 * dynamically computes the prefix/suffix for the language fragment from binary expressions.
 * <p/>
 * It also tries to deal with the "glued token" problem by removing or adding whitespace to the prefix/suffix.
 */
public final class CustomLanguageInjector implements ProjectComponent {

  private final Project myProject;
  private final Configuration myInjectionConfiguration;

  @SuppressWarnings({"unchecked"})
  private final List<Pair<SmartPsiElementPointer<PsiLanguageInjectionHost>, InjectedLanguage>> myTempPlaces = new ArrayList();

  public CustomLanguageInjector(Project project, Configuration configuration) {
    myProject = project;
    myInjectionConfiguration = configuration;
  }

  public void initComponent() {
    PsiManager.getInstance(myProject).registerLanguageInjector(new MyLanguageInjector(this));
    ReferenceProvidersRegistry.getInstance(myProject)
        .registerReferenceProvider(TrueFilter.INSTANCE, PsiLiteralExpression.class, new LanguageReferenceProvider());
  }


  @Nullable
  List<Pair<InjectedLanguage, TextRange>> getInjectedLanguage(PsiElement place) {
    synchronized (myTempPlaces) {
      for (Iterator<Pair<SmartPsiElementPointer<PsiLanguageInjectionHost>, InjectedLanguage>> it = myTempPlaces.iterator(); it.hasNext();) {
        final Pair<SmartPsiElementPointer<PsiLanguageInjectionHost>, InjectedLanguage> pair = it.next();
        final PsiLanguageInjectionHost element = pair.first.getElement();
        if (element == null) {
          it.remove();
        }
        else if (element == place) {
          if (element instanceof XmlAttributeValue || element instanceof PsiLiteralExpression) {
            return Collections.singletonList(Pair.create(pair.second, TextRange.from(1, element.getTextLength() - 2)));
          }
          else {
            return Collections.singletonList(Pair.create(pair.second, TextRange.from(0, element.getTextLength())));
          }
        }
      }
    }

    final PsiElement child = place.getFirstChild();
    if (child != null && child instanceof PsiJavaToken) {
      if (((PsiJavaToken)child).getTokenType() == JavaTokenType.STRING_LITERAL) {
        final PsiLiteralExpression psiExpression = (PsiLiteralExpression)place;

        final List<Pair<InjectedLanguage, TextRange>> list = getAnnotationInjections(psiExpression, place);
        if (list != null) {
          return list;
        }

        final List<MethodParameterInjection> injections = myInjectionConfiguration.getParameterInjections();
        for (MethodParameterInjection injection : injections) {
          if (injection.isApplicable(psiExpression)) {
            return convertInjection(injection, psiExpression);
          }
        }
      }
    }
    else if (place instanceof XmlText) {
      final XmlText xmlText = (XmlText)place;

      List<Pair<InjectedLanguage, TextRange>> list = null;
      for (XmlTagInjection injection : myInjectionConfiguration.getTagInjections()) {
        if (injection.isApplicable(xmlText)) {
          final List<Pair<InjectedLanguage, TextRange>> l = convertInjection(injection, xmlText);
          if (list == null) {
            list = l;
          }
          else {
            list = addInjections(list, l);
          }
          if (injection.isTerminal()) {
            break;
          }
        }
      }
      return list;
    }
    else if (place instanceof XmlAttributeValue) {
      final XmlAttributeValue value = (XmlAttributeValue)place;

      // Check that we don't inject anything into embedded (e.g. JavaScript) content:
      // XmlToken: "
      // JSEmbeddedContent
      // XmlToken "

      // Actually IDEA shouldn't ask for injected languages at all in this case.
      final PsiElement[] children = value.getChildren();
      if (children.length < 3 ||
          !(children[1] instanceof XmlToken) ||
          ((XmlToken)children[1]).getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
        return null;
      }

      List<Pair<InjectedLanguage, TextRange>> list = null;
      for (XmlAttributeInjection injection : myInjectionConfiguration.getAttributeInjections()) {
        if (injection.isApplicable(value)) {
          final List<Pair<InjectedLanguage, TextRange>> l = convertInjection(injection, value);
          if (list == null) {
            list = l;
          }
          else {
            list = addInjections(list, l);
          }
          if (injection.isTerminal()) {
            break;
          }
        }
      }
      return list;
    }
    return null;
  }

  private static List<Pair<InjectedLanguage, TextRange>> addInjections(List<Pair<InjectedLanguage, TextRange>> list,
                                                                       List<Pair<InjectedLanguage, TextRange>> l) {
    // check whether different injections intersect with each other, which must not happen
    for (Iterator<Pair<InjectedLanguage, TextRange>> it = l.iterator(); it.hasNext();) {
      final Pair<InjectedLanguage, TextRange> p1 = it.next();
      for (Pair<InjectedLanguage, TextRange> p2 : list) {
        if (p1.second.intersects(p2.second)) {
          it.remove();
          break;
        }
      }
    }
    if (!l.isEmpty()) {
      if (!(list instanceof ArrayList)) {
        list = new ArrayList<Pair<InjectedLanguage, TextRange>>(list);
      }
      list.addAll(l);
    }
    return list;
  }

  @Nullable
  private List<Pair<InjectedLanguage, TextRange>> getAnnotationInjections(PsiLiteralExpression psiExpression, PsiElement place) {
    final PsiModifierListOwner element =
        AnnotationUtilEx.getAnnotatedElementFor(psiExpression, AnnotationUtilEx.LookupType.PREFER_DECLARATION);
    if (element != null) {
      final PsiAnnotation[] annotations =
          AnnotationUtilEx.getAnnotationFrom(element, myInjectionConfiguration.getLanguageAnnotationPair(), true);
      if (annotations.length > 0) {
        final String id = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
        final String prefix = AnnotationUtilEx.calcAnnotationValue(annotations, "prefix");
        final String suffix = AnnotationUtilEx.calcAnnotationValue(annotations, "suffix");

        return getInjectionWithContext(place, id, prefix, suffix);
      }
    }
    return null;
  }

  private static List<Pair<InjectedLanguage, TextRange>> getInjectionWithContext(PsiElement place,
                                                                                 String langId,
                                                                                 String prefix,
                                                                                 String suffix) {
    final ContextComputationProcessor processor = ContextComputationProcessor.calcContext(place);
    if (processor != null) {
      final StringBuilder p = new StringBuilder();
      final StringBuilder s = new StringBuilder();

      p.append(prefix);
      processor.getPrefix(p);
      processor.getSuffix(s);
      s.append(suffix);

      return Collections.singletonList(
          Pair.create(InjectedLanguage.create(langId, p.toString(), s.toString(), true), TextRange.from(1, place.getTextLength() - 2)));
    }
    else {
      return Collections
          .singletonList(Pair.create(InjectedLanguage.create(langId, "", "", false), TextRange.from(1, place.getTextLength() - 2)));
    }
  }

  private static <T extends PsiElement> List<Pair<InjectedLanguage, TextRange>> convertInjection(Injection<T> injection, T element) {
    final List<TextRange> list = injection.getInjectedArea(element);
    if (list.size() == 0) {
      return Collections.emptyList();
    }
    else if (list.size() == 1 && element instanceof PsiLiteralExpression) {
      return getInjectionWithContext(element, injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix());
    }
    return ContainerUtil.map2List(list, new InjectionConverter<T>(injection));
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "IntelliLang.CustomLanguageInjector";
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void addTempInjection(PsiLanguageInjectionHost host, InjectedLanguage selectedValue) {
    final SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
    final SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = manager.createSmartPsiElementPointer(host);

    synchronized (myTempPlaces) {
      myTempPlaces.add(Pair.create(pointer, selectedValue));
    }
  }

  private static class InjectionConverter<T extends PsiElement> implements Function<TextRange, Pair<InjectedLanguage, TextRange>> {
    private final Injection<T> myInjection;

    public InjectionConverter(Injection<T> injection) {
      myInjection = injection;
    }

    public Pair<InjectedLanguage, TextRange> fun(TextRange s) {
      final InjectedLanguage l =
          InjectedLanguage.create(myInjection.getInjectedLanguageId(), myInjection.getPrefix(), myInjection.getSuffix(), false);
      return Pair.create(l, s);
    }
  }

  private static class MyLanguageInjector implements com.intellij.psi.LanguageInjector {
    private CustomLanguageInjector myInjector;

    MyLanguageInjector(CustomLanguageInjector injector) {
      this.myInjector = injector;
    }

    public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
      final List<Pair<InjectedLanguage, TextRange>> lang = myInjector.getInjectedLanguage(host);
      if (lang != null) {
        for (Pair<InjectedLanguage, TextRange> pair : lang) {
          final InjectedLanguage l = pair.first;
          if (l != null) {
            final Language language = l.getLanguage();
            // if language isn't injected when length == 0, subsequent edits will not cause the language to be injected as well.
            // Maybe IDEA core is caching a bit too aggressively here?
            if (language != null/* && (pair.second.getLength() > 0*/) {
              if (l.isDynamic()) {
                // Only adjust prefix/suffix if it has been computed dynamically. Otherwise some other
                // useful cases may break. This system is far from perfect still...
                final StringBuilder prefix = new StringBuilder(l.getPrefix());
                final StringBuilder suffix = new StringBuilder(l.getSuffix());
                adjustPrefixAndSuffix(getUnescapedText(host, pair.second.substring(host.getText())), prefix, suffix);

                addPlaceSafe(injectionPlacesRegistrar, language, pair.second, prefix.toString(), suffix.toString());
              }
              else {
                addPlaceSafe(injectionPlacesRegistrar, language, pair.second, l.getPrefix(), l.getSuffix());
              }
            }
          }
        }
      }
    }

    private static void addPlaceSafe(InjectedLanguagePlaces registrar,
                                     Language language,
                                     TextRange textRange,
                                     String prefix,
                                     String suffix) {
      try {
        registrar.addPlace(language, textRange, prefix, suffix);
      }
      catch (AssertionError e) {
        logError(registrar, language, textRange, e);
      }
      catch (Exception e) {
        logError(registrar, language, textRange, e);
      }
    }

    private static void logError(InjectedLanguagePlaces registrar, Language language, TextRange textRange, Throwable e) {
      final Logger log = Logger.getInstance(CustomLanguageInjector.class.getName());
      final String place = registrar.toString() +
                           ": [" +
                           (registrar instanceof PsiElement ? ((PsiElement)registrar).getText() : "<n/a>") +
                           " - " +
                           textRange +
                           "]";
      log.info("Failed to inject language '" + language.getID() + "' into '" + place + "'. Possibly there are overlapping injection areas.",
               e);
    }

    private static String getUnescapedText(PsiLanguageInjectionHost host, String text) {
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
  }

  public static CustomLanguageInjector getInstance(Project project) {
    return project.getComponent(CustomLanguageInjector.class);
  }
}
