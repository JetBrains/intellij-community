// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ClashingTraitMethodsInspectionBase extends BaseInspection {
  protected static final Logger LOG = Logger.getInstance(ClashingTraitMethodsInspectionBase.class);

  protected static @NotNull List<ClashingMethod> collectClassingMethods(@NotNull GrTypeDefinition typeDefinition) {
    Collection<HierarchicalMethodSignature> visibleSignatures = typeDefinition.getVisibleSignatures();

    List<ClashingMethod> clashingMethods = new ArrayList<>();
    for (HierarchicalMethodSignature signature : visibleSignatures) {
      PsiMethod method = signature.getMethod();
      if (method instanceof GrTraitMethod && method.getContainingClass() == typeDefinition) {
        List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
        if (superSignatures.size() > 1) {
          List<GrTypeDefinition> traits = new ArrayList<>();
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

  private static @NotNull @Unmodifiable List<PsiClass> collectImplementedTraits(@NotNull GrTypeDefinition typeDefinition) {
    return ContainerUtil.findAll(typeDefinition.getSupers(), aClass -> GrTraitUtil.isTrait(aClass));
  }

  @Override
  protected @NotNull BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
        super.visitTypeDefinition(typeDefinition);

        List<PsiClass> superTraits = collectImplementedTraits(typeDefinition);

        if (superTraits.size() < 2) return;

        List<ClashingMethod> clashingMethods = collectClassingMethods(typeDefinition);

        for (ClashingMethod clashing : clashingMethods) {
          registerError(typeDefinition.getNameIdentifierGroovy(), buildWarning(clashing), new LocalQuickFix[]{getFix()}, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }

      private @NotNull @InspectionMessage String buildWarning(@NotNull ClashingMethod entry) {
        return GroovyBundle.message("inspection.message.traits.0.contain.clashing.methods.with.signature.1", buildTraitString(entry),
                                    buildSignatureString(entry));
      }

      private static @NotNull @NlsSafe String buildSignatureString(@NotNull ClashingMethod entry) {
        HierarchicalMethodSignature signature = entry.getSignature();
        return PsiFormatUtil.formatMethod(signature.getMethod(), signature.getSubstitutor(),
                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                          PsiFormatUtilBase.SHOW_TYPE);
      }

      private static @NotNull @NlsSafe String buildTraitString(@NotNull ClashingMethod entry) {
        return StringUtil.join(entry.getSuperTraits(), tr -> tr.getName(), ", ");
      }
    };
  }

  protected @NotNull LocalQuickFix getFix(){
    return GroovyFix.EMPTY_FIX;
  }

  protected static class ClashingMethod {
    private final HierarchicalMethodSignature mySignature;
    private final List<GrTypeDefinition> mySuperTraits;

    public ClashingMethod(@NotNull HierarchicalMethodSignature signature, @NotNull List<GrTypeDefinition> superTraits) {
      mySignature = signature;
      mySuperTraits = superTraits;
    }

    public @NotNull HierarchicalMethodSignature getSignature() {
      return mySignature;
    }

    public @NotNull List<GrTypeDefinition> getSuperTraits() {
      return mySuperTraits;
    }
  }
}
