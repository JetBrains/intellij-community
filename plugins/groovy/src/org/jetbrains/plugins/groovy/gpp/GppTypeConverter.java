package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.toplevel.AnnotatedContextFilter;
import org.jetbrains.plugins.groovy.lang.psi.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;

/**
 * @author peter
 */
public class GppTypeConverter extends GrTypeConverter {

  public static boolean hasTypedContext(PsiElement context) {
    if (context == null) {
      return false;
    }

    if (AnnotatedContextFilter.hasAnnotatedContext(context, "groovy.lang.Typed")) {
      return true;
    }

    final VirtualFile vfile = context.getContainingFile().getOriginalFile().getVirtualFile();
    if (vfile != null) {
      final String extension = vfile.getExtension();
      if ("gpp".equals(extension) || "grunit".equals(vfile.getExtension())) {
        return true;
      }
    }

    return false;
  }

  @Override
  public Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context) {
    if (rType instanceof GrTupleType) {
      final GrTupleType tupleType = (GrTupleType)rType;

      final PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(lType, false);
      if (expectedComponent != null &&
          isMethodCallConversion(context) && TypesUtil.isAssignable(expectedComponent, tupleType.getParameters()[0], context) && 
          hasDefaultConstructor(lType)) {
        return true;
      }

      if (lType instanceof PsiClassType && hasConstructor((PsiClassType)lType, tupleType.getComponentTypes(), context)) {
        return true;
      }
    }
    else if (rType instanceof GrMapType) {
      final PsiType lKeyType = PsiUtil.substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_MAP, 0, false);
      final PsiType lValueType = PsiUtil.substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_MAP, 1, false);
      final PsiType[] parameters = ((GrMapType)rType).getParameters();
      if (lKeyType != null && lValueType != null &&
          parameters[0] != null && parameters[1] != null &&
          (!TypesUtil.isAssignable(lKeyType, parameters[0], context) || !TypesUtil.isAssignable(lValueType, parameters[1], context))) {
        return null;
      }

      if (((GrMapType)rType).getValueType("super") != null) {
        return true;
      }

      if (!isMethodCallConversion(context) && hasDefaultConstructor(lType)) {
        return true;
      }
    }
    else if (rType instanceof GrClosureType) {
      final PsiType[] methodParameters = GppClosureParameterTypeProvider.findSingleAbstractMethodSignature(lType);
      final GrClosureSignature signature = ((GrClosureType)rType).getSignature();
      if (methodParameters != null && GrClosureSignatureUtil.isSignatureApplicable(signature, methodParameters, context)) {
        return true;
      }
    }

    return null;
  }

  private static boolean hasDefaultConstructor(PsiType type) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass != null && PsiUtil.hasDefaultConstructor(psiClass, true);

  }

  private static boolean hasConstructor(PsiClassType lType, PsiType[] argTypes, GroovyPsiElement context) {
    return org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getConstructorCandidates(lType, argTypes, context).length == 1;
  }

}
