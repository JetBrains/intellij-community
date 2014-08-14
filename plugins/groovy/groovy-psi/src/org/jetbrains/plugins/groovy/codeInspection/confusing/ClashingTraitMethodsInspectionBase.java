/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import java.util.Collection;
import java.util.List;

public abstract class ClashingTraitMethodsInspectionBase extends BaseInspection {
  protected static final Logger LOG = Logger.getInstance(ClashingTraitMethodsInspectionBase.class);

  @NotNull
  protected static List<ClashingMethod> collectClassingMethods(@NotNull GrTypeDefinition typeDefinition) {
    Collection<HierarchicalMethodSignature> visibleSignatures = typeDefinition.getVisibleSignatures();

    List<ClashingMethod> clashingMethods = ContainerUtil.newArrayList();
    for (HierarchicalMethodSignature signature : visibleSignatures) {
      PsiMethod method = signature.getMethod();
      if (method instanceof GrTraitMethod && method.getContainingClass() == typeDefinition) {
        List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
        if (superSignatures.size() > 1) {
          List<GrTypeDefinition> traits = ContainerUtil.newArrayList();
          for (HierarchicalMethodSignature superSignature : superSignatures) {
            PsiMethod superMethod = superSignature.getMethod();
            PsiClass superClass = superMethod.getContainingClass();
            if (GrTraitUtil.isTrait(superClass) &&
                !superMethod.getModifierList().hasExplicitModifier(PsiModifier.ABSTRACT)) {
              traits.add((GrTypeDefinition)superClass);
            }
          }

          if (traits.size() > 1) {
            clashingMethods.add(new ClashingMethod(signature, traits));
          }
        }
      }
    }

    return clashingMethods;
  }

  @NotNull
  private static List<PsiClass> collectImplementedTraits(@NotNull GrTypeDefinition typeDefinition) {
    return ContainerUtil.findAll(typeDefinition.getSupers(), new Condition<PsiClass>() {
      @Override
      public boolean value(PsiClass aClass) {
        return GrTraitUtil.isTrait(aClass);
      }
    });
  }

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
        super.visitTypeDefinition(typeDefinition);

        List<PsiClass> superTraits = collectImplementedTraits(typeDefinition);

        if (superTraits.size() < 2) return;

        List<ClashingMethod> clashingMethods = collectClassingMethods(typeDefinition);

        for (ClashingMethod clashing : clashingMethods) {
          registerError(typeDefinition.getNameIdentifierGroovy(), buildWarning(clashing), new LocalQuickFix[]{getFix()}, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }

      @NotNull
      private String buildWarning(@NotNull ClashingMethod entry) {
        return "Traits " + buildTraitString(entry) + " contain clashing methods with signature " + buildSignatureString(entry);
      }

      @NotNull
      private String buildSignatureString(@NotNull ClashingMethod entry) {
        HierarchicalMethodSignature signature = entry.getSignature();
        return PsiFormatUtil.formatMethod(signature.getMethod(), signature.getSubstitutor(),
                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                          PsiFormatUtilBase.SHOW_TYPE);
      }

      @NotNull
      private String buildTraitString(@NotNull ClashingMethod entry) {
        return StringUtil.join(entry.getSuperTraits(), new Function<GrTypeDefinition, String>() {
          @Override
          public String fun(GrTypeDefinition tr) {
            return tr.getName();
          }
        }, ", ");
      }
    };
  }

  @NotNull
  protected LocalQuickFix getFix(){
    return GroovyFix.EMPTY_FIX;
  }

  protected static class ClashingMethod {
    private final HierarchicalMethodSignature mySignature;
    private final List<GrTypeDefinition> mySuperTraits;

    public ClashingMethod(@NotNull HierarchicalMethodSignature signature, @NotNull List<GrTypeDefinition> superTraits) {
      mySignature = signature;
      mySuperTraits = superTraits;
    }

    @NotNull
    public HierarchicalMethodSignature getSignature() {
      return mySignature;
    }

    @NotNull
    public List<GrTypeDefinition> getSuperTraits() {
      return mySuperTraits;
    }
  }
}
