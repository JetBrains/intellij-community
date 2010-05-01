package org.jetbrains.plugins.groovy.gpp;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author peter
 */
public class GppTypeConverter extends GrTypeConverter {

  private static boolean isTyped(@Nullable PsiModifierListOwner member) {
    if (member == null) {
      return false;
    }

    final PsiModifierList modifierList = member.getModifierList();
    if (modifierList != null && modifierList.findAnnotation("groovy.lang.Typed") != null) {
      return true;
    }

    if (member instanceof GrMethod) {
      return isTyped(((GrMethod)member).getContainingClass());
    }
    if (member instanceof GrTypeDefinition) {
      final PsiFile file = member.getContainingFile();
      if (file instanceof GroovyFile) {
        return isTyped(JavaPsiFacade.getInstance(member.getProject()).findPackage(((GroovyFile)file).getPackageName()));
      }
    }
    return false;
  }

  @Override
  public Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context) {
    if (!isTyped(PsiTreeUtil.getParentOfType(context, GrMember.class))) {
      return null;
    }


    if (rType instanceof GrTupleType) {
      final PsiType[] componentTypes = ((GrTupleType)rType).getComponentTypes();

      final PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(lType, false);
      if (expectedComponent != null && allTypesAssignable(componentTypes, expectedComponent, context) && hasDefaultConstructor(lType)) {
        return true;
      }

      if (lType instanceof PsiClassType && hasConstructor((PsiClassType)lType, componentTypes, context)) {
        return true;
      }

      return null;
    }
    else if (rType instanceof GrMapType) {
      final PsiType expectedKey = PsiUtil.substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_MAP, 0, false);
      final PsiType expectedValue = PsiUtil.substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_MAP, 1, false);
      if (expectedKey != null && expectedValue != null && hasDefaultConstructor(lType)) {
        final GrMapType mapType = (GrMapType)rType;
        if (allTypesAssignable(mapType.getAllKeyTypes(), expectedKey, context) &&
            allTypesAssignable(mapType.getAllValueTypes(), expectedValue, context)) {
          return true;
        }
        return null;
      }

      if (hasDefaultConstructor(lType)) {
        // maps are casted to any objects
        // todo check for unimlemented abstract methods
        return true;
      }

      return null;
    }

    
    return null;
  }

  private static boolean hasDefaultConstructor(PsiType type) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null) {
      return false;
    }

    final PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) {
      return true;
    }
    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().getParametersCount() == 0) {
        return true;
      }
      //todo Groovy constructors with an (empty) Map argument
    }
    return false;
  }

  private static boolean hasConstructor(PsiClassType lType, PsiType[] argTypes, GroovyPsiElement context) {
    final PsiClassType.ClassResolveResult resolveResult = lType.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (psiClass == null) {
      return false;
    }

    final GroovyResolveResult grResult = resolveResult instanceof GroovyResolveResult
                                         ? (GroovyResolveResult)resolveResult
                                         : new GroovyResolveResultImpl(psiClass, context, substitutor, true, true);
    final GroovyResolveResult[] candidates = org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getConstructorCandidates(
      context, new GroovyResolveResult[]{grResult}, argTypes);
    return candidates.length == 1;
  }

  private static boolean allTypesAssignable(PsiType[] types, PsiType to, GroovyPsiElement context) {
    for (PsiType component : types) {
      if (!TypesUtil.isAssignable(to, component, context)) {
        return false;
      }
    }
    return true;
  }
}
