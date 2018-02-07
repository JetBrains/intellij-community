// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameJavaMethodProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class RenameAliasImportedMethodProcessor extends RenameJavaMethodProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof GroovyPsiElement && super.canProcessElement(element);
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(@NotNull PsiElement element) {
    return RenameAliasedUsagesUtil.filterAliasedRefs(super.findReferences(element), element);
  }

  @NotNull
  @Override
  public RenameDialog createRenameDialog(@NotNull Project project, @NotNull PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
    return new RenameDialog(project, element, nameSuggestionContext, editor) {
      @Override
      protected boolean areButtonsValid() {
        return true;
      }
    };
  }

  @Override
  public void renameElement(@NotNull PsiElement psiElement,
                            @NotNull String newName,
                            @NotNull UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    boolean isGetter = GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)psiElement);
    boolean isSetter = GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)psiElement);

    List<UsageInfo> methodAccess = new ArrayList<>(usages.length);
    List<UsageInfo> propertyAccess = new ArrayList<>(usages.length);

    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element instanceof GrReferenceExpression && ((GrReferenceExpression)element).advancedResolve().isInvokedOnProperty()) {
        propertyAccess.add(usage);
      }
      else {
        methodAccess.add(usage);
      }
    }

    super.renameElement(psiElement, newName, methodAccess.toArray(UsageInfo.EMPTY_ARRAY), listener);

    final String propertyName;
    if (isGetter) {
      propertyName = GroovyPropertyUtils.getPropertyNameByGetterName(newName, true);
    }
    else if (isSetter) {
      propertyName = GroovyPropertyUtils.getPropertyNameBySetterName(newName);
    }
    else {
      propertyName = null;
    }

    if (propertyName == null) {
      //it means accessor is renamed to not-accessor and we should replace all property-access-refs with method-access-refs

      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
      for (UsageInfo info : propertyAccess) {
        final PsiElement element = info.getElement();
        if (element instanceof GrReferenceExpression) {
          final PsiElement qualifier = ((GrReferenceExpression)element).getQualifier();
          String qualifierPrefix = qualifier == null ? "" : qualifier.getText() + ".";
          if (isGetter) {
            final GrExpression call = factory.createExpressionFromText(qualifierPrefix + newName + "()");
            ((GrReferenceExpression)element).replaceWithExpression(call, true);
          }
          else {
            final PsiElement parent = element.getParent();
            assert parent instanceof GrAssignmentExpression;
            final GrExpression rValue = ((GrAssignmentExpression)parent).getRValue();
            final GrExpression call =
              factory.createExpressionFromText(qualifierPrefix + newName + "(" + (rValue == null ? "" : rValue.getText()) + ")");
            ((GrAssignmentExpression)parent).replaceWithExpression(call, true);
          }
        }
      }
    }
    else {
      for (UsageInfo usage : propertyAccess) {
        final PsiReference ref = usage.getReference();
        if (ref != null) {
          ref.handleElementRename(propertyName);
        }
      }
    }
  }

  @Override
  public void findCollisions(@NotNull PsiElement element,
                             @NotNull final String newName,
                             @NotNull final Map<? extends PsiElement, String> allRenames,
                             @NotNull final List<UsageInfo> result) {
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      OverridingMethodsSearch.search(method).forEach(overrider -> {
        PsiElement original = overrider;
        if (overrider instanceof PsiMirrorElement) {
          original = ((PsiMirrorElement)overrider).getPrototype();
        }

        if (original instanceof SyntheticElement) return true;

        if (original instanceof GrField) {
          result.add(new FieldNameCollisionInfo((GrField)original, method));
        }
        return true;
      });
    }

    final ListIterator<UsageInfo> iterator = result.listIterator();
    while (iterator.hasNext()) {
      final UsageInfo info = iterator.next();
      final PsiElement ref = info.getElement();
      if (ref instanceof GrReferenceExpression || ref == null) continue;
      if (!RenameUtil.isValidName(element.getProject(), ref, newName)) {
        iterator.add(new UnresolvableCollisionUsageInfo(ref, element) {
          @Override
          public String getDescription() {
            return RefactoringBundle.message("0.is.not.an.identifier", newName, ref.getText());
          }
        });
      }
    }
  }

  @Nullable
  @Override
  protected PsiElement processRef(PsiReference ref, String newName) {
    PsiElement element = ref.getElement();
    if (RenameUtil.isValidName(element.getProject(), element, newName) || element instanceof GrReferenceElement) {
      return super.processRef(ref, newName);
    }

    PsiElement nameElement;
    if (element instanceof PsiReferenceExpression) {
      nameElement = ((PsiReferenceExpression)element).getReferenceNameElement();
    }
    else {
      return null;
    }
    TextRange range = nameElement.getTextRange();
    Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(nameElement.getContainingFile());
    document.replaceString(range.getStartOffset(), range.getEndOffset(), newName);

    return null;
  }

  private static class FieldNameCollisionInfo extends UnresolvableCollisionUsageInfo {
    private final String myName;
    private final String myBaseName;

    public FieldNameCollisionInfo(GrField field, PsiMethod baseMethod) {
      super(field, field);
      myName = field.getName();
      myBaseName = baseMethod.getName();
    }

    @Override
    public String getDescription() {
      return GroovyRefactoringBundle.message("cannot.rename.property.0", myName, myBaseName);
    }
  }
}
