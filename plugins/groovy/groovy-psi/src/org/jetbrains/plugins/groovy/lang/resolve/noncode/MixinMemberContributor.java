// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.noncode;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class MixinMemberContributor {
  public static boolean processClassMixins(@NotNull final PsiType qualifierType,
                                           @NotNull PsiScopeProcessor processor,
                                           @NotNull final PsiElement place,
                                           @NotNull ResolveState state) {
    if (isInAnnotation(place)) return true;

    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(qualifierType);
    if (aClass == null) return true;
    if (aClass.getLanguage() != GroovyLanguage.INSTANCE) return true;

    final PsiModifierList modifierList = aClass.getModifierList();
    if (modifierList == null) return true;

    List<PsiClass> mixins = new ArrayList<>();
    for (PsiAnnotation annotation : getAllMixins(modifierList)) {
      final PsiAnnotationMemberValue value = annotation.findAttributeValue("value");

      if (value instanceof GrAnnotationArrayInitializer) {
        final GrAnnotationMemberValue[] initializers = ((GrAnnotationArrayInitializer)value).getInitializers();
        for (GrAnnotationMemberValue initializer : initializers) {
          addMixin(initializer, mixins);
        }
      }
      else if (value instanceof GrExpression) {
        addMixin((GrExpression)value, mixins);
      }
    }

    final MixinProcessor delegate = new MixinProcessor(processor, qualifierType, place);
    for (PsiClass mixin : mixins) {
      if (!mixin.processDeclarations(delegate, state, null, place)) {
        return false;
      }
    }

    return true;
  }

  public static String getOriginInfoForCategory(PsiMethod element) {
    PsiClass aClass = element.getContainingClass();
    if (aClass != null && aClass.getName() != null) {
      return "mixed in from " + aClass.getName();
    }
    return "mixed in";
  }

  public static String getOriginInfoForMixin(@NotNull PsiType subjectType) {
    return "mixed in " + subjectType.getPresentableText();
  }

  private static List<PsiAnnotation> getAllMixins(PsiModifierList modifierList) {
    final ArrayList<PsiAnnotation> result = new ArrayList<>();
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      if (GroovyCommonClassNames.GROOVY_LANG_MIXIN.equals(annotation.getQualifiedName())) {
        result.add(annotation);
      }
    }
    return result;
  }

  private static boolean isInAnnotation(PsiElement place) {
    return place.getParent() instanceof GrAnnotation || place.getParent() instanceof GrAnnotationArrayInitializer;
  }

  private static void addMixin(GrAnnotationMemberValue value, List<? super PsiClass> mixins) {
    if (value instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)value).resolve();
      if (resolved instanceof PsiClass) {
        mixins.add((PsiClass)resolved);
      }
    }
  }

  private static class MixinedMethod extends LightMethod implements OriginInfoAwareElement, PsiMirrorElement {
    private final String myOriginInfo;
    private final PsiMethod myPrototype;

    MixinedMethod(@NotNull PsiMethod method, String originInfo) {
      super(method.getManager(), method, ObjectUtils.assertNotNull(method.getContainingClass()));
      myOriginInfo = originInfo;
      myPrototype = method;
    }

    @Nullable
    @Override
    public String getOriginInfo() {
      return myOriginInfo;
    }

    @NotNull
    @Override
    public PsiElement getPrototype() {
      return myPrototype;
    }
  }

  public static class MixinProcessor extends DelegatingScopeProcessor {
    private final PsiType myType;
    private final PsiElement myPlace;

    public MixinProcessor(PsiScopeProcessor delegate, @NotNull PsiType qualifierType, @Nullable PsiElement place) {
      super(delegate);
      myType = qualifierType;
      myPlace = place;
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if (element instanceof PsiMethod && GdkMethodUtil.isCategoryMethod((PsiMethod)element, myType, myPlace, state.get(PsiSubstitutor.KEY))) {
        PsiMethod method = (PsiMethod)element;
        String originInfo = getOriginInfoForCategory(method);
        return super.execute(GrGdkMethodImpl.createGdkMethod(method, false, originInfo), state);
      }
      else if (element instanceof PsiMethod) {
        return super.execute(new MixinedMethod((PsiMethod)element, getOriginInfoForMixin(myType)), state);
      }
      else {
        return super.execute(element, state);
      }
    }
  }
}
