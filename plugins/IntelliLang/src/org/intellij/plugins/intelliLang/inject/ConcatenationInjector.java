package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.PairProcessor;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.ContextComputationProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author cdr
 */
public class ConcatenationInjector implements ConcatenationAwareInjector {
  private final Configuration myInjectionConfiguration;
  private long myConfigurationModificationCount;
  private MultiValuesMap<Trinity<String, Integer, Integer>, MethodParameterInjection> myMethodCache;

  public ConcatenationInjector(Configuration configuration) {
    myInjectionConfiguration = configuration;
  }

  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull PsiElement... operands) {
    final PsiFile containingFile = operands[0].getContainingFile();
    processLiteralExpressionInjections(new PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>>() {
      public boolean process(final Language language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list) {
        CustomLanguageInjector.registerInjection(language, list, containingFile, registrar);
        return true;
      }
    }, operands);
  }

  private boolean processAnnotationInjections(final PsiModifierListOwner annoElement, final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor,
                                              final PsiElement... operands) {
    final PsiAnnotation[] annotations =
      AnnotationUtilEx.getAnnotationFrom(annoElement, myInjectionConfiguration.getLanguageAnnotationPair(), true);
    if (annotations.length > 0) {
      final String id = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
      final String prefix = AnnotationUtilEx.calcAnnotationValue(annotations, "prefix");
      final String suffix = AnnotationUtilEx.calcAnnotationValue(annotations, "suffix");
      final MethodParameterInjection injection = new MethodParameterInjection();
      if (prefix != null) injection.setPrefix(prefix);
      if (suffix != null) injection.setSuffix(suffix);
      if (id != null) injection.setInjectedLanguageId(id);
      processInjectionWithContext(false, injection, processor, operands);
      return true;
    }
    return false;
  }
  private void processLiteralExpressionInjections(final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor,
                                                  final PsiElement... operands) {
    if (operands.length == 0) return;
    final PsiElement firstOperand = operands[0];
    final PsiElement topBlock = PsiUtil.getTopLevelEnclosingCodeBlock(firstOperand, null);
    final LocalSearchScope searchScope = new LocalSearchScope(new PsiElement[]{topBlock instanceof PsiCodeBlock
                                                                               ? topBlock : firstOperand.getContainingFile()}, "", true);
    final THashSet<PsiModifierListOwner> visitedVars = new THashSet<PsiModifierListOwner>();
    final LinkedList<PsiElement> places = new LinkedList<PsiElement>();
    places.add(firstOperand);
    boolean unparsable = false;
    while (!places.isEmpty()) {
      final PsiElement curPlace = places.removeFirst();
      final PsiModifierListOwner owner = AnnotationUtilEx.getAnnotatedElementFor(curPlace, AnnotationUtilEx.LookupType.PREFER_CONTEXT);
      if (owner == null) continue;
      if (processAnnotationInjections(owner, processor, operands)) return; // annotated element

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
          processInjectionWithContext(unparsable, injection, processor, operands);
          if (injection.isTerminal()) {
            return;
          }
        }
      }
    }
  }

  private static void processInjectionWithContext(final boolean unparsable, final MethodParameterInjection injection,
                                                  final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor,
                                                  final PsiElement... operands) {
    final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
    if (language == null) return;
    final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

    final Ref<Boolean> unparsableRef = Ref.create(unparsable);
    final List<Object> objects = ContextComputationProcessor.collectOperands(injection.getPrefix(), injection.getSuffix(), unparsableRef, operands);
    if (objects.isEmpty()) return;
    final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result = new ArrayList<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>();
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
        if (!(curHost instanceof PsiLiteralExpression)) {
          result.add(Trinity.create(curHost, InjectedLanguage.create(injection.getInjectedLanguageId(), curPrefix, curSuffix, true),
                                  ElementManipulators.getManipulator(curHost).getRangeInElement(curHost)));
        }
        else {
          for (TextRange textRange : injection.getInjectedArea(((PsiLiteralExpression)curHost))) {
            result.add(Trinity.create(curHost, InjectedLanguage.create(injection.getInjectedLanguageId(), curPrefix, curSuffix, true),
                                    textRange));
          }
        }
      }
    }
    if (!result.isEmpty()) {
      if (separateFiles) {
        for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
          processor.process(language, Collections.singletonList(trinity));
        }
      }
      else {
        for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
          trinity.first.putUserData(CustomLanguageInjector.HAS_UNPARSABLE_FRAGMENTS, unparsableRef.get());
        }
        processor.process(language, result);
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
}
