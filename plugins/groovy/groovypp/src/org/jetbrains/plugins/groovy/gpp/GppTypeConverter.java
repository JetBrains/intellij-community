package org.jetbrains.plugins.groovy.gpp;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
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
  public Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull PsiElement context) {
    if (!isTyped(PsiTreeUtil.getParentOfType(context, GrMember.class))) {
      return null;
    }

    final PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(lType, false);
    if (expectedComponent == null) {
      return null;
    }

    if (rType instanceof GrTupleType) {
      for (PsiType component : ((GrTupleType)rType).getComponentTypes()) {
        if (!TypesUtil.isAssignable(expectedComponent, component, context)) {
          return null;
        }
      }
      return true;
    }
    
    return null;
  }
}
