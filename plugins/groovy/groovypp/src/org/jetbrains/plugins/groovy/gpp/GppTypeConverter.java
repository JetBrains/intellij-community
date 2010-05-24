package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;

/**
 * @author peter
 */
public class GppTypeConverter extends GrTypeConverter {

  public static boolean hasTypedContext(PsiElement context) {
    if (context == null) {
      return false;
    }

    if (context instanceof PsiModifierListOwner && isTyped(((PsiModifierListOwner)context).getModifierList())) {
      return true;
    }

    final GrMember parentMember = PsiTreeUtil.getContextOfType(context, GrMember.class, true);
    if (parentMember != null) {
      return hasTypedContext(parentMember);
    }

    final PsiFile file = context.getContainingFile();
    if (file instanceof GroovyFile) {
      final GrPackageDefinition packageDefinition = ((GroovyFile)file).getPackageDefinition();
      if (packageDefinition != null && isTyped(packageDefinition.getAnnotationList())) {
        return true;
      }

      final VirtualFile vfile = file.getVirtualFile();
      if (vfile != null) {
        final String extension = vfile.getExtension();
        if ("gpp".equals(extension) || "grunit".equals(vfile.getExtension())) {
          return true;
        }
      }

      return false;
    }
    return false;
  }

  private static boolean isTyped(PsiModifierList modifierList) {
    return modifierList != null && modifierList.findAnnotation("groovy.lang.Typed") != null;
  }

  @Override
  public Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context) {
    if (rType instanceof GrTupleType) {
      final PsiType[] componentTypes = ((GrTupleType)rType).getComponentTypes();

      final PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(lType, false);
      if (expectedComponent != null && hasDefaultConstructor(lType)) {
        return true;
      }

      if (lType instanceof PsiClassType && hasConstructor((PsiClassType)lType, componentTypes, context)) {
        return true;
      }

      return null;
    }
    else if (rType instanceof GrMapType) {
      if (hasDefaultConstructor(lType)) {
        return true;
      }

      return null;
    }
    else if (rType instanceof GrClosureType) {
      final PsiType[] methodParameters = GppClosureParameterTypeProvider.findSingleAbstractMethodSignature(lType);
      final GrClosureSignature signature = ((GrClosureType)rType).getSignature();
      if (methodParameters != null && GrClosureSignatureUtil.isSignatureApplicable(signature, methodParameters, context)) {
        return true;
      }
      return false;
    }


    return null;
  }

  private static boolean hasDefaultConstructor(PsiType type) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass != null && PsiUtil.hasDefaultConstructor(psiClass, true);

  }

  private static boolean hasConstructor(PsiClassType lType, PsiType[] argTypes, GroovyPsiElement context) {
    return getConstructorCandidates(lType, argTypes, context).length == 1;
  }

  public static GroovyResolveResult[] getConstructorCandidates(PsiClassType classType, PsiType[] argTypes, GroovyPsiElement context) {
    final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (psiClass == null) {
      return GroovyResolveResult.EMPTY_ARRAY;
    }

    final GroovyResolveResult grResult = resolveResult instanceof GroovyResolveResult
                                         ? (GroovyResolveResult)resolveResult
                                         : new GroovyResolveResultImpl(psiClass, context, substitutor, true, true);
    return org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getConstructorCandidates(context, new GroovyResolveResult[]{grResult}, argTypes);
  }

}
