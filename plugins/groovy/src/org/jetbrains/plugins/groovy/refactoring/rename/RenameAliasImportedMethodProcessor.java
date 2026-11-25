// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.modplan.rename.ModPlanRenameUtil;
import com.intellij.refactoring.rename.ModPlanJavaRenameOptions;
import com.intellij.refactoring.rename.RenameJavaMethodProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class RenameAliasImportedMethodProcessor extends RenameJavaMethodProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof GroovyPsiElement && super.canProcessElement(element);
  }

  @Override
  public @NotNull Collection<PsiReference> findReferences(@NotNull PsiElement element,
                                                          @NotNull SearchScope searchScope,
                                                          boolean searchInCommentsAndStrings) {
    return RenameAliasedUsagesUtil.filterAliasedRefs(super.findReferences(element, searchScope, searchInCommentsAndStrings), element);
  }

  @Override
  public void renameElement(@NotNull PsiElement psiElement,
                            @NotNull String newName,
                            UsageInfo @NotNull [] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    boolean isGetter = GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)psiElement);

    List<UsageInfo> methodAccess = new ArrayList<>(usages.length);
    List<UsageInfo> propertyAccess = new ArrayList<>(usages.length);

    for (UsageInfo usage : usages) {
      if (isPropertyAccess(usage)) {
        propertyAccess.add(usage);
      }
      else {
        methodAccess.add(usage);
      }
    }

    super.renameElement(psiElement, newName, methodAccess.toArray(UsageInfo.EMPTY_ARRAY), listener);

    final String propertyName = propertyNameAfterRename((PsiMethod)psiElement, newName);

    if (propertyName == null) {
      //it means accessor is renamed to not-accessor and we should replace all property-access-refs with method-access-refs
      for (UsageInfo info : propertyAccess) {
        final PsiElement element = info.getElement();
        if (element instanceof GrReferenceExpression) {
          replaceWithCall((GrReferenceExpression)element, newName, isGetter);
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
                             final @NotNull String newName,
                             final @NotNull Map<? extends PsiElement, String> allRenames,
                             final @NotNull List<UsageInfo> result) {
    if (element instanceof PsiMethod method) {
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

  @Override
  protected @Nullable PsiElement processRef(PsiReference ref, String newName) {
    PsiElement element = ref.getElement();
    if (RenameUtil.isValidName(element.getProject(), element, newName) || element instanceof GrReferenceElement) {
      return super.processRef(ref, newName);
    }

    PsiElement nameElement;
    if (element instanceof PsiReferenceExpression expression) {
      nameElement = expression.getReferenceNameElement();
    }
    else {
      return null;
    }
    TextRange range = nameElement.getTextRange();
    Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(nameElement.getContainingFile());
    document.replaceString(range.getStartOffset(), range.getEndOffset(), newName);

    return null;
  }

  /**
   * @return the name of the property that an access gets in the rename of {@code method} to {@code newName}; null if {@code method} is no
   * accessor, or if {@code newName} is the name of no accessor
   */
  static @Nullable String propertyNameAfterRename(@NotNull PsiMethod method, @NotNull String newName) {
    if (GroovyPropertyUtils.isSimplePropertyGetter(method)) {
      return GroovyPropertyUtils.getPropertyNameByGetterName(newName, true);
    }
    if (GroovyPropertyUtils.isSimplePropertySetter(method)) {
      return GroovyPropertyUtils.getPropertyNameBySetterName(newName);
    }
    return null;
  }

  /**
   * Replaces {@code element}, a property access to an accessor that gets {@code newName}, the name of no accessor, with a call: a read
   * becomes a call of the getter, and a write becomes a call of the setter with the assigned value.
   */
  static void replaceWithCall(@NotNull GrReferenceExpression element, @NotNull String newName, boolean isGetter) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
    final PsiElement qualifier = element.getQualifier();
    String qualifierPrefix = qualifier == null ? "" : qualifier.getText() + ".";
    if (isGetter) {
      final GrExpression call = factory.createExpressionFromText(qualifierPrefix + newName + "()");
      element.replaceWithExpression(call, true);
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

  /** @return whether {@code usage} is a property access to the method, which {@link #renameElement} writes apart from the calls */
  private static boolean isPropertyAccess(@NotNull UsageInfo usage) {
    return usage.getElement() instanceof GrReferenceExpression reference && reference.advancedResolve().isInvokedOnProperty();
  }

  /**
   * The rename of a Groovy method on the ModPlan engine. It asks about the base method as the Java provider does, and writes a property
   * access to an accessor as {@link #renameElement} does.
   */
  @ApiStatus.Internal
  public static class ModPlanGrMethodRenameProvider extends ModPlanGroovyRenameProvider {
    @SuppressWarnings("unused")
    public ModPlanGrMethodRenameProvider() {
      this(RenameAliasImportedMethodProcessor.class);
    }

    protected ModPlanGrMethodRenameProvider(@NotNull Class<? extends RenameAliasImportedMethodProcessor> processorClass) {
      super(processorClass);
    }

    @Override
    protected @NotNull List<ModPlanQuestion> substitutionQuestions(@NotNull SymbolPointer subject, @NotNull PsiElement element) {
      ModPlanQuestion question = element instanceof PsiMethod method ? baseMethodQuestion(subject, method) : null;
      return question == null ? List.of() : List.of(question);
    }

    @Override
    protected @Nullable PsiElement substitute(@NotNull PsiElement element, @NotNull ModPlanOptionValues answers) {
      if (!(element instanceof PsiMethod)) return element;
      return substituteElementToRename(element, null, false, answers.get(ModPlanJavaRenameOptions.RENAME_BASE_METHOD));
    }

    /**
     * The calls get the new name first. Then a property access gets the new name of the property, or becomes a call. A reference out of
     * Groovy where the new name is no identifier keeps its text, because {@link #findCollisions} gives it a collision. The default edits
     * would give a property access the name of the method:
     * <pre>{@code
     * print new Foo().foo      // the rename of getFoo to getBar: new Foo().bar, not new Foo().getBar
     * print new Foo().foo      // the rename of getFoo to get: new Foo().get()
     * print new Foo().foo = 2  // the rename of setFoo to set: new Foo().set(2)
     * }</pre>
     */
    @Override
    protected @Nullable List<ModPlanEdit> referenceEdits(@NotNull PsiElement element,
                                                         @NotNull String newName,
                                                         @NotNull List<UsageInfo> references,
                                                         @NotNull Map<PsiElement, String> renamed) {
      if (!(element instanceof PsiMethod method)) return super.referenceEdits(element, newName, references, renamed);
      String propertyName = propertyNameAfterRename(method, newName);
      boolean isGetter = GroovyPropertyUtils.isSimplePropertyGetter(method);
      List<UsageInfo> methodAccess = new ArrayList<>();
      List<ModPlanEdit> propertyAccessEdits = new ArrayList<>();
      for (UsageInfo usage : references) {
        PsiElement usageElement = usage.getElement();
        if (isPropertyAccess(usage)) {
          GrReferenceExpression reference = (GrReferenceExpression)usageElement;
          if (propertyName != null) {
            List<ModPlanEdit> edits = ModPlanRenameUtil.referenceEdits(List.of(usage), propertyName);
            if (edits == null) return null;
            propertyAccessEdits.addAll(edits);
          }
          else if (reference != null && isGetter) {
            propertyAccessEdits.add(ModPlanEdit.update(reference, copy -> replaceWithCall(copy, newName, true)));
          }
          else if (reference != null && reference.getParent() instanceof GrAssignmentExpression assignment) {
            propertyAccessEdits.add(ModPlanEdit.update(assignment, copy -> {
              if (copy.getLValue() instanceof GrReferenceExpression lValue) replaceWithCall(lValue, newName, false);
            }));
          }
          else {
            return null;
          }
        }
        else if (usageElement instanceof GrReferenceElement<?> || usageElement == null ||
                 RenameUtil.isValidName(usageElement.getProject(), usageElement, newName)) {
          methodAccess.add(usage);
        }
      }
      List<ModPlanEdit> methodAccessEdits = super.referenceEdits(element, newName, methodAccess, renamed);
      if (methodAccessEdits == null) return null;
      return ContainerUtil.concat(methodAccessEdits, propertyAccessEdits);
    }
  }

  private static class FieldNameCollisionInfo extends UnresolvableCollisionUsageInfo {
    private final String myName;
    private final String myBaseName;

    FieldNameCollisionInfo(GrField field, PsiMethod baseMethod) {
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
