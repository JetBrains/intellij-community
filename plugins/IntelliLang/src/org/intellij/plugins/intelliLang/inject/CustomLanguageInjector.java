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
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.PairProcessor;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.ContextComputationProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public final class CustomLanguageInjector implements ProjectComponent {
  private static final Comparator<TextRange> RANGE_COMPARATOR = new Comparator<TextRange>() {
    public int compare(final TextRange o1, final TextRange o2) {
      if (o1.intersects(o2)) return 0;
      return o1.getStartOffset() - o2.getStartOffset();
    }
  };

  private final Project myProject;
  private final Configuration myInjectionConfiguration;
  private long myConfigurationModificationCount;
  private MultiValuesMap<Trinity<String, Integer, Integer>, MethodParameterInjection> myMethodCache;

  @SuppressWarnings({"unchecked"})
  private final List<Pair<SmartPsiElementPointer<PsiLanguageInjectionHost>, InjectedLanguage>> myTempPlaces = new ArrayList();
  static final Key<Boolean> HAS_UNPARSABLE_FRAGMENTS = Key.create("HAS_UNPARSABLE_FRAGMENTS");

  public CustomLanguageInjector(Project project, Configuration configuration) {
    myProject = project;
    myInjectionConfiguration = configuration;
  }

  public void initComponent() {
    InjectedLanguageManager.getInstance(myProject).registerMultiHostInjector(new MyLanguageInjector(this));
    ReferenceProvidersRegistry.getInstance(myProject)
        .registerReferenceProvider(TrueFilter.INSTANCE, PsiLiteralExpression.class, new LanguageReferenceProvider());
  }

  private void getInjectedLanguage(final PsiElement place, final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor) {
    // optimization
    if (place instanceof PsiLiteralExpression && !isStringLiteral(place)) return;
    
    synchronized (myTempPlaces) {
      for (Iterator<Pair<SmartPsiElementPointer<PsiLanguageInjectionHost>, InjectedLanguage>> it = myTempPlaces.iterator(); it.hasNext();) {
        final Pair<SmartPsiElementPointer<PsiLanguageInjectionHost>, InjectedLanguage> pair = it.next();
        final PsiLanguageInjectionHost element = pair.first.getElement();
        if (element == null) {
          it.remove();
        }
        else if (element == place) {
          processor.process(pair.second.getLanguage(), Collections.singletonList(Trinity.create(element, pair.second, ElementManipulators.getManipulator(element).getRangeInElement(element))));
          return;
        }
      }
    }

    if (place instanceof PsiExpression) {
      processLiteralExpressionInjections(findFirstLiteralExpression((PsiExpression)place), processor);
    }
    else if (place instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)place;
      for (final XmlTagInjection injection : myInjectionConfiguration.getTagInjections()) {
        if (injection.isApplicable(xmlTag)) {
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
            final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
            if (language == null) continue;
            for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
              trinity.first.putUserData(HAS_UNPARSABLE_FRAGMENTS, hasSubTags.get());
            }
            processor.process(language, result);
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
          final List<TextRange> ranges = injection.getInjectedArea(value);
          if (ranges.isEmpty()) continue;
          final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
          if (language == null) continue;
          final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result = new ArrayList<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>();
          final InjectedLanguage l = InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);
          for (TextRange textRange : ranges) {
            result.add(Trinity.create((PsiLanguageInjectionHost)value, l, textRange));
          }
          processor.process(language, result);
          if (injection.isTerminal()) {
            break;
          }
        }
      }
    }
    else {
      for (CustomLanguageInjectorExtension o : Extensions.getExtensions(CustomLanguageInjectorExtension.EP_NAME)) {
        o.getInjectedLanguage(myInjectionConfiguration, place, processor);
      }
    }
  }

  private static boolean isStringLiteral(final PsiElement place) {
    if (place instanceof PsiLiteralExpression) {
      final PsiElement child = place.getFirstChild();
      if (child != null && child instanceof PsiJavaToken) {
        if (((PsiJavaToken)child).getTokenType() == JavaTokenType.STRING_LITERAL) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private static PsiLiteralExpression findFirstLiteralExpression(final PsiExpression expression) {
    if (isStringLiteral(expression)) return (PsiLiteralExpression)expression;
    final LinkedList<PsiElement> list = new LinkedList<PsiElement>();
    list.add(expression);
    while (!list.isEmpty()) {
      final PsiElement element = list.removeFirst();
      if (element instanceof PsiCallExpression) continue;  // IDEADEV-28384 - TODO: other cases?
      if (isStringLiteral(element)) {
        return (PsiLiteralExpression)element;
      }
      list.addAll(0, Arrays.asList(element.getChildren()));
    }
    return null;
  }

  private void processLiteralExpressionInjections(@Nullable final PsiLiteralExpression firstLiteral, final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor) {
    if (firstLiteral == null) return;
    final PsiElement topBlock = PsiUtil.getTopLevelEnclosingCodeBlock(firstLiteral, null);
    final LocalSearchScope searchScope = new LocalSearchScope(new PsiElement[]{topBlock instanceof PsiCodeBlock? topBlock : firstLiteral.getContainingFile()}, "", true);
    final THashSet<PsiModifierListOwner> visitedVars = new THashSet<PsiModifierListOwner>();
    final LinkedList<PsiExpression> places = new LinkedList<PsiExpression>();
    places.add(firstLiteral);
    boolean unparsable = false;
    while (!places.isEmpty()) {
      final PsiExpression curPlace = places.removeFirst();
      final PsiModifierListOwner owner = AnnotationUtilEx.getAnnotatedElementFor(curPlace, AnnotationUtilEx.LookupType.PREFER_CONTEXT);
      if (owner == null) continue;
      if (processAnnotationInjections(firstLiteral, owner, processor)) return; // annotated element

      final PsiMethod psiMethod;
      final Trinity<String, Integer, Integer> trin;
      if (owner instanceof PsiParameter) {
        psiMethod = PsiTreeUtil.getParentOfType(owner, PsiMethod.class, false);
        final PsiParameterList parameterList = psiMethod == null? null : psiMethod.getParameterList();
        // don't check catchblock parameters & etc.
        if (parameterList == null || parameterList != owner.getParent()) continue;
        trin = Trinity.create(psiMethod.getName(), parameterList.getParametersCount(),
                              parameterList.getParameterIndex((PsiParameter)owner));
      }
      else if (owner instanceof PsiMethod) {
        psiMethod = (PsiMethod)owner;
        trin = Trinity.create(psiMethod.getName(), psiMethod.getParameterList().getParametersCount(), -1);
      }
      else if (myInjectionConfiguration.isResolveReferences() &&
               owner instanceof PsiVariable && visitedVars.add(owner)) {
        final PsiVariable variable = (PsiVariable)owner;
        for (PsiReference psiReference : ReferencesSearch.search(variable, searchScope).findAll()) {
          final PsiElement element = psiReference.getElement();
          if (element instanceof PsiExpression) {
            final PsiExpression refExpression = (PsiExpression)element;
            places.add(refExpression);
            if (!unparsable) {
              unparsable = checkUnparsableReference(refExpression);
            }
          }
        }
        continue;
      }
      else {
        continue;
      }
      final Collection<MethodParameterInjection> injections = getMethodCache().get(trin);
      if (injections == null) return;
      for (MethodParameterInjection injection : injections) {
        if (injection.isApplicable(psiMethod)) {
          processInjectionWithContext(firstLiteral, unparsable, injection.getInjectedLanguageId(), injection.getPrefix(),
                                      injection.getSuffix(), processor);
          return;
        }
      }
    }
  }

  private static boolean checkUnparsableReference(final PsiExpression refExpression) {
    final PsiElement parent = refExpression.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      final IElementType operation = assignmentExpression.getOperationTokenType();
      if (assignmentExpression.getLExpression() == refExpression && JavaTokenType.PLUSEQ.equals(operation)) {
        return true;
      }
    }
    else if (parent instanceof PsiBinaryExpression) {
      return true;
    }
    return false;
  }

  private MultiValuesMap<Trinity<String, Integer, Integer>, MethodParameterInjection> getMethodCache() {
    if (myMethodCache != null && myInjectionConfiguration.getModificationCount() == myConfigurationModificationCount) {
      return myMethodCache;
    }
    myConfigurationModificationCount = myInjectionConfiguration.getModificationCount();
    final MultiValuesMap<Trinity<String, Integer, Integer>, MethodParameterInjection> tmpMap =
        new MultiValuesMap<Trinity<String, Integer, Integer>, MethodParameterInjection>();
    for (MethodParameterInjection injection : myInjectionConfiguration.getParameterInjections()) {
      for (MethodParameterInjection.MethodInfo info : injection.getMethodInfos()) {
        final boolean[] flags = info.getParamFlags();
        for (int i = 0; i < flags.length; i++) {
          if (!flags[i]) continue;
          tmpMap.put(Trinity.create(info.getMethodName(), flags.length, i), injection);
        }
        if (info.isReturnFlag()) {
          tmpMap.put(Trinity.create(info.getMethodName(), 0, -1), injection);
        }
      }
    }
    myMethodCache = tmpMap;
    return tmpMap;
  }

  private boolean processAnnotationInjections(@NotNull PsiLiteralExpression firstLiteral, final PsiModifierListOwner annoElement, final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor) {
    final PsiAnnotation[] annotations =
      AnnotationUtilEx.getAnnotationFrom(annoElement, myInjectionConfiguration.getLanguageAnnotationPair(), true);
    if (annotations.length > 0) {
      final String id = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
      final String prefix = AnnotationUtilEx.calcAnnotationValue(annotations, "prefix");
      final String suffix = AnnotationUtilEx.calcAnnotationValue(annotations, "suffix");
      processInjectionWithContext(firstLiteral, false, id, prefix, suffix, processor);
      return true;
    }
    return false;
  }

  private static void processInjectionWithContext(@NotNull final PsiLiteralExpression place, final boolean unparsable, final String langId,
                                                  final String prefix,
                                                  final String suffix,
                                                  final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor) {
    final Ref<Boolean> unparsableRef = Ref.create(unparsable);
    final List<Object> objects = ContextComputationProcessor.collectOperands(place, prefix, suffix, unparsableRef);
    if (objects.isEmpty()) return;
    final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list = new ArrayList<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>();
    final int len = objects.size();
    for (int i = 0; i < len; i++) {
      String curPrefix = null;
      Object o = objects.get(i);
      if (o instanceof String) {
        curPrefix = (String)o;
        if (i == len - 1) return; // IDEADEV-26751
        o = objects.get(++i);
      }
      String curSuffix = null;
      PsiLanguageInjectionHost curHost = null;
      if (o instanceof PsiLanguageInjectionHost) {
        curHost = (PsiLanguageInjectionHost)o;
        if (i == len-2) {
          final Object next = objects.get(i + 1);
          if (next instanceof String) {
            i ++;
            curSuffix = (String)next;
          }
        }
      }
      if (curHost == null) {
        unparsableRef.set(Boolean.TRUE);
      }
      else {
        list.add(Trinity.create(curHost, InjectedLanguage.create(langId, curPrefix, curSuffix, true),
                                ElementManipulators.getManipulator(curHost).getRangeInElement(curHost)));
      }
    }
    for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : list) {
      trinity.first.putUserData(HAS_UNPARSABLE_FRAGMENTS, unparsableRef.get());
    }
    processor.process(InjectedLanguage.findLanguageById(langId), list);
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

  private static class MyLanguageInjector implements MultiHostInjector {
    private CustomLanguageInjector myInjector;

    MyLanguageInjector(CustomLanguageInjector injector) {
      myInjector = injector;
    }


    @NotNull
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
      final THashSet<Class<? extends PsiElement>> elements = new THashSet<Class<? extends PsiElement>>();
      for (CustomLanguageInjectorExtension o : Extensions.getExtensions(CustomLanguageInjectorExtension.EP_NAME)) {
        o.elementsToInjectIn(elements);
      }
      elements.addAll(Arrays.asList(PsiLiteralExpression.class, XmlTag.class, XmlAttributeValue.class, PsiBinaryExpression.class));
      return Arrays.<Class<? extends PsiElement>>asList(elements.toArray(new Class[elements.size()]));
    }

    public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull PsiElement host) {
      final TreeSet<TextRange> ranges = new TreeSet<TextRange>(RANGE_COMPARATOR);
      final PsiFile containingFile = host.getContainingFile();
      myInjector.getInjectedLanguage(host, new PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>>() {
        public boolean process(final Language language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list) {
          // if language isn't injected when length == 0, subsequent edits will not cause the language to be injected as well.
          // Maybe IDEA core is caching a bit too aggressively here?
          if (language == null/* && (pair.second.getLength() > 0*/) {
            return true;
          }
          for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : list) {
            if (ranges.contains(trinity.third.shiftRight(trinity.first.getTextRange().getStartOffset()))) return true;
          }
          boolean injectionStarted = false;
          for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : list) {
            final PsiLanguageInjectionHost host = trinity.first;
            if (host.getContainingFile() != containingFile) continue;

            final TextRange textRange = trinity.third;
            final InjectedLanguage injectedLanguage = trinity.second;
            ranges.add(textRange.shiftRight(host.getTextRange().getStartOffset()));

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

              addPlaceSafe(registrar, prefix.toString(), suffix.toString(), host, textRange, language);
            }
            else {
              addPlaceSafe(registrar, injectedLanguage.getPrefix(), injectedLanguage.getSuffix(), host, textRange, language);
            }
          }
          //try {
          if (injectionStarted) {
            registrar.doneInjecting();
          }
            //}
            //catch (AssertionError e) {
            //  logError(language, host, null, e);
            //}
            //catch (Exception e) {
            //  logError(language, host, null, e);
            //}

          return true;
        }
      });
    }

    private static void addPlaceSafe(MultiHostRegistrar registrar, String prefix, String suffix, PsiLanguageInjectionHost host, TextRange textRange,
                                     Language language) {
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
  }

  public static CustomLanguageInjector getInstance(Project project) {
    return project.getComponent(CustomLanguageInjector.class);
  }

}
