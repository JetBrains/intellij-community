// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.control;

import com.google.common.collect.Lists;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.*;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeInfoImpl;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureProcessor;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrParameterInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.refactoring.changeSignature.ParameterInfo.NEW_PARAMETER;

/**
 * @author Maxim.Medvedev
 */
public class CreateParameterForFieldIntention extends Intention {
  private static final Logger LOG = Logger.getInstance(CreateParameterForFieldIntention.class);
  private static final Key<CachedValue<List<GrField>>> FIELD_CANDIDATES = Key.create("Fields.candidates");

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull final Project project, final Editor editor)
    throws IncorrectOperationException {
    final List<GrField> candidates = findFieldCandidates(element);
    if (candidates != null) {
      performForConstructor(element, project, editor, candidates);
    }
    else {
      final List<GrMethod> constructors = findConstructorCandidates(element);
      performForField(element, project, editor, constructors);
    }
  }

  private static void performForField(PsiElement element, final Project project, Editor editor, List<GrMethod> constructors) {
    final GrField field = PsiTreeUtil.getParentOfType(element, GrField.class);
    if (constructors.isEmpty()) return;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (GrMethod constructor : constructors) {
        addParameter(field, constructor, project);
      }
      return;
    }

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(constructors)
      .setRenderer(new MethodCellRenderer(true))
      .setTitle(GroovyIntentionsBundle.message("create.parameter.for.field.intention.name")).
      setMovable(true).
                    setItemsChosenCallback((values) -> {
        ArrayList<GrMethod> selectedValues = Lists.newArrayList(values);
        selectedValues.sort((o1, o2) -> o2.getParameterList().getParametersCount() - o1.getParameterList().getParametersCount());
        CommandProcessor.getInstance().executeCommand(project, () -> {
          for (GrMethod selectedValue : selectedValues) {
            LOG.assertTrue(selectedValue.isValid());
            addParameter(field, selectedValue, project);
          }
        }, GroovyIntentionsBundle.message("create.parameter.for.field.intention.name"), null);
      }).createPopup().showInBestPositionFor(editor);
  }

  private static void performForConstructor(PsiElement element, final Project project, Editor editor, List<GrField> candidates) {
    final GrMethod constructor = PsiTreeUtil.getParentOfType(element, GrMethod.class);
    if (candidates.isEmpty()) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (GrField candidate : candidates) {
        addParameter(candidate, constructor, project);
      }
      return;
    }

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(candidates)
      .setRenderer(new DefaultPsiElementCellRenderer())
      .setTitle(GroovyIntentionsBundle.message("create.parameter.for.field.intention.name")).
      setMovable(true).
                    setItemsChosenCallback((selectedValues) -> CommandProcessor.getInstance().executeCommand(project, () -> {
                      for (GrField selectedValue : selectedValues) {
                        LOG.assertTrue(selectedValue.isValid());
                        addParameter(selectedValue, constructor, project);
                      }
                    }, GroovyIntentionsBundle.message("create.parameter.for.field.intention.name"), null)).createPopup().showInBestPositionFor(editor);
  }

  private static void addParameter(final GrField selectedValue, final GrMethod constructor, final Project project) {
    List<GrParameterInfo> parameters = new ArrayList<>();
    GrParameter[] constructorParameters = constructor.getParameters();
    for (int i = 0; i < constructorParameters.length; i++) {
      parameters.add(new GrParameterInfo(constructorParameters[i], i));
    }
    final String[] suggestedNames =
      JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, selectedValue.getName(), null, null).names;

    final DefaultGroovyVariableNameValidator nameValidator =
      new DefaultGroovyVariableNameValidator(constructor, Collections.emptyList(), false);
    String parameterName = ContainerUtil.find(suggestedNames, name -> !nameValidator.validateName(name, false).isEmpty());

    if (parameterName == null) {
      parameterName = nameValidator.validateName(suggestedNames[0], true);
    }
    parameters.add(new GrParameterInfo(parameterName, "null", "", selectedValue.getTypeGroovy(), NEW_PARAMETER, false));

    PsiClassType[] exceptionTypes = constructor.getThrowsList().getReferencedTypes();
    ThrownExceptionInfo[] thrownExceptionInfos = new ThrownExceptionInfo[exceptionTypes.length];
    for (int i = 0; i < exceptionTypes.length; i++) {
      thrownExceptionInfos[i] = new JavaThrownExceptionInfo(i, exceptionTypes[i]);
    }

    final GrChangeInfoImpl grChangeInfo = new GrChangeInfoImpl(constructor, null, null, constructor.getName(), parameters, thrownExceptionInfos, false);

    final String finalParameterName = parameterName;
    final GrChangeSignatureProcessor processor = new GrChangeSignatureProcessor(project, grChangeInfo) {
      @Override
      protected void performRefactoring(UsageInfo @NotNull [] usages) {
        super.performRefactoring(usages);

        final GrOpenBlock block = constructor.getBlock();
        LOG.assertTrue(block != null);
        final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

        final String text;
        if (StringUtil.equals(selectedValue.getName(), finalParameterName)) {
          text = "this." + selectedValue.getName() + " = " + finalParameterName;
        }
        else {
          text = selectedValue.getName() + " = " + finalParameterName;
        }

        final GrStatement assignment = factory.createStatementFromText(text);
        final GrStatement statement = block.addStatementBefore(assignment, null);
        final GrReferenceExpression ref = (GrReferenceExpression)((GrAssignmentExpression)statement).getLValue();
        if (!PsiManager.getInstance(project).areElementsEquivalent(ref.resolve(), selectedValue)) {
          PsiUtil.qualifyMemberReference(ref, selectedValue, selectedValue.getName());
        }

      }
    };
    processor.run();

  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  static class MyPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(@NotNull PsiElement element) {
      final List<GrField> candidates = findFieldCandidates(element);
      if (candidates != null && !candidates.isEmpty()) return true;
      final List<GrMethod> constructors = findConstructorCandidates(element);
      return constructors != null && !constructors.isEmpty();
    }
  }

  @Nullable
  private static List<GrField> findFieldCandidates(PsiElement element) {
    final GrMethod constructor = PsiTreeUtil.getParentOfType(element, GrMethod.class);
    if (constructor == null || !constructor.isConstructor()) return null;
    if (constructor.getBlock() == null) return null;
    if (PsiTreeUtil.isAncestor(constructor.getBlock(), element, false)) {
      return null;
    }
    final PsiClass clazz = constructor.getContainingClass();

    if (!(clazz instanceof GrTypeDefinition)) return null;
    return findCandidatesCached(constructor, (GrTypeDefinition)clazz);
  }

  private static List<GrField> findCandidates(PsiMethod constructor, final GrTypeDefinition clazz) {
    final List<GrField> usedFields = new ArrayList<>();
    final GrOpenBlock block = constructor instanceof GrMethod ? ((GrMethod)constructor).getBlock() : null;
    if (block == null) {
      return usedFields;
    }
    
    final PsiManager manager = clazz.getManager();
    block.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        final PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof GrField &&
            manager.areElementsEquivalent(((GrField)resolved).getContainingClass(), clazz) &&
            PsiUtil.isAccessedForWriting(referenceExpression)) {
          usedFields.add((GrField)resolved);
        }
      }

      @Override
      public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
      }

      @Override
      public void visitClosure(@NotNull GrClosableBlock closure) {
      }
    });

    List<GrField> fields = new ArrayList<>();
    for (final GrField field : clazz.getFields()) {
      if (field.getInitializerGroovy() != null) continue;
      if (ContainerUtil.find(usedFields, (Condition<PsiField>)o -> manager.areElementsEquivalent(o, field)) == null) {
        fields.add(field);
      }
    }

    return fields;
  }

  private static List<GrField> findCandidatesCached(final PsiMethod constructor, final GrTypeDefinition clazz) {
    final CachedValue<List<GrField>> value = constructor.getUserData(FIELD_CANDIDATES);
    if (value != null && value.getValue() != null) return value.getValue();
    final CachedValue<List<GrField>> cachedValue =
      CachedValuesManager.getManager(constructor.getProject()).createCachedValue(
        () -> CachedValueProvider.Result.create(findCandidates(constructor, clazz), PsiModificationTracker.MODIFICATION_COUNT), false);
    constructor.putUserData(FIELD_CANDIDATES, cachedValue);
    return cachedValue.getValue();
  }


  @Nullable
  private static List<GrMethod> findConstructorCandidates(PsiElement element) {
    final GrField field = PsiTreeUtil.getParentOfType(element, GrField.class);
    if (field == null) return null;
    PsiClass containingClass = field.getContainingClass();
    if (!(containingClass instanceof GrTypeDefinition)) return null;
    return findConstructorCandidates(field, (GrTypeDefinition)containingClass);
  }

  private static List<GrMethod> findConstructorCandidates(@NotNull final GrField field, @NotNull GrTypeDefinition psiClass) {
    final List<GrMethod> result = new ArrayList<>();
    final PsiMethod[] constructors = psiClass.getConstructors();
    final PsiManager manager = field.getManager();
    for (PsiMethod constructor : constructors) {
      final List<GrField> fields = findCandidatesCached(constructor, psiClass);
      if (ContainerUtil.find(fields, grField -> manager.areElementsEquivalent(grField, field)) != null) {
        result.add((GrMethod)constructor);
      }
    }
    return result;
  }
}
