package org.jetbrains.plugins.groovy.spoc;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrShiftExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrBitwiseExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class SpocMemberContributor extends NonCodeMembersContributor {

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     GroovyPsiElement place,
                                     ResolveState state) {
    if (!(qualifierType instanceof PsiClassType)) return;

    ClassHint classHint = processor.getHint(ClassHint.KEY);
    if (classHint != null && !classHint.shouldProcess(ClassHint.ResolveKind.PROPERTY)) return;

    PsiClass aClass = ((PsiClassType)qualifierType).resolve();
    if (!(aClass instanceof GrClassDefinition)) return; // Optimization: Spoc-test must be a Groovy class.

    if (!GroovyPsiManager.isInheritorCached(aClass, SpocUtils.SPEC_CLASS_NAME)) return;

    GrMethod method = PsiTreeUtil.getParentOfType(place, GrMethod.class);
    if (method == null) return;

    if (aClass != method.getContainingClass()) return;

    PsiFile containingFile = aClass.getContainingFile();
    if (containingFile != containingFile.getOriginalFile()) {
      PsiElement originalPlace = containingFile.getOriginalFile().findElementAt(place.getTextOffset());
      method = PsiTreeUtil.getParentOfType(originalPlace, GrMethod.class);
      if (method == null) return;
    }

    Map<String, SpocVariableDescriptor> cachedValue = SpocUtils.getVariableMap(method);

    String nameHint = ResolveUtil.getNameHint(processor);
    if (nameHint == null) {
      for (SpocVariableDescriptor spocVar : cachedValue.values()) {
        if (!processor.execute(spocVar.getVariable(), state)) return;
      }
    }
    else {
      SpocVariableDescriptor spocVar = cachedValue.get(nameHint);
      if (spocVar != null && spocVar.getNavigationElement() != place) {
        if (!processor.execute(spocVar.getVariable(), state)) return;
      }
    }
  }

}
