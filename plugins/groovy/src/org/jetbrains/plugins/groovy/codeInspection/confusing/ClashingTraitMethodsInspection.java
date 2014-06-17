package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import java.util.Collection;
import java.util.List;

/**
 * Created by Max Medvedev on 03/06/14
 */
public class ClashingTraitMethodsInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance(ClashingTraitMethodsInspection.class);

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
          registerError(typeDefinition.getNameIdentifierGroovy(), buildWarning(clashing), new LocalQuickFix[]{new MyQuickFix()}, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
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
          public String fun(GrTypeDefinition tr) {
            return tr.getName();
          }
        }, ", ");
      }
    };
  }

  @NotNull
  private static List<ClashingMethod> collectClassingMethods(@NotNull GrTypeDefinition typeDefinition) {
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

  private static class ClashingMethod {
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

  private static class MyQuickFix implements LocalQuickFix {
    private static final int MAX_SIGNATURE_LENGTH = 50;

    private static String buildSignature(HierarchicalMethodSignature signature, int maxLength) {
      StringBuilder result = new StringBuilder();
      result.append(signature.getName());

      PsiType[] params = signature.getParameterTypes();

      if (params.length == 0) {
        result.append("()");
        return result.toString();
      }

      result.append("(");

      for (PsiType param : params) {
        if (result.length() >= maxLength - "...)".length()) {
          result.append("...)");
          return result.toString();
        }
        result.append(param.getPresentableText());
        result.append(", ");
      }
      result.replace(result.length() - ", ".length(), result.length(), ")");
      return result.toString();
    }

    @NotNull
    @Override
    public String getName() {
      return GroovyInspectionBundle.message("declare.explicit.implementations.of.trait");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Declare explicit implementation of clashing traits";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull final ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element.getParent();
      if (parent instanceof GrTypeDefinition && ((GrTypeDefinition)parent).getNameIdentifierGroovy() == element) {
        final GrTypeDefinition aClass = (GrTypeDefinition)parent;

        new WriteCommandAction(project, aClass.getContainingFile()) {
          @Override
          protected void run(@NotNull Result result) {
            final List<ClashingMethod> clashingMethods = collectClassingMethods(aClass);

            for (ClashingMethod method : clashingMethods) {
              PsiMethod traitMethod = method.getSignature().getMethod();
              LOG.assertTrue(traitMethod instanceof GrTraitMethod);
              OverrideImplementUtil.overrideOrImplement(aClass, traitMethod);
            }
          }
        }.execute();
      }
    }
  }
}
