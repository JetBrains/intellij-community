/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.ui.components.JBList;
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

import javax.swing.*;
import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class CreateParameterForFieldIntention extends Intention {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.intentions.control.CreateParameterForFieldIntention");

  @NotNull
  @Override
  public String getText() {
    return super.getText();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, final Project project, final Editor editor)
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
    if (constructors.size() == 0) return;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (GrMethod constructor : constructors) {
        addParameter(field, constructor, project);
      }
      return;
    }

    final JList list = new JBList(constructors.toArray(new GrMethod[constructors.size()]));
    list.setCellRenderer(new MethodCellRenderer(true));

    new PopupChooserBuilder(list).setTitle(GroovyIntentionsBundle.message("create.parameter.for.field.intention.name")).
      setMovable(true).
      setItemChoosenCallback(new Runnable() {
        public void run() {
          final Object[] selectedValues = list.getSelectedValues();
          Arrays.sort(selectedValues, new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
              return ((GrMethod)o2).getParameterList().getParametersCount() - ((GrMethod)o1).getParameterList().getParametersCount();
            }
          });
          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            @Override
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  for (Object selectedValue : selectedValues) {
                    LOG.assertTrue(((GrMethod)selectedValue).isValid());
                    addParameter(field, ((GrMethod)selectedValue), project);
                  }
                }
              });
            }
          }, GroovyIntentionsBundle.message("create.parameter.for.field.intention.name"), null);
        }
      }).createPopup().showInBestPositionFor(editor);

  }

  private static void performForConstructor(PsiElement element, final Project project, Editor editor, List<GrField> candidates) {
    final GrMethod constructor = PsiTreeUtil.getParentOfType(element, GrMethod.class);
    if (candidates.size() == 0) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (GrField candidate : candidates) {
        addParameter(candidate, constructor, project);
      }
      return;
    }
    final JList list = new JBList(candidates.toArray(new GrField[candidates.size()]));
    list.setCellRenderer(new DefaultPsiElementCellRenderer());

    new PopupChooserBuilder(list).setTitle(GroovyIntentionsBundle.message("create.parameter.for.field.intention.name")).
      setMovable(true).
      setItemChoosenCallback(new Runnable() {
        public void run() {
          final Object[] selectedValues = list.getSelectedValues();
          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            @Override
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  for (Object selectedValue : selectedValues) {
                    LOG.assertTrue(((GrField)selectedValue).isValid());
                    addParameter(((GrField)selectedValue), constructor, project);
                  }
                }
              });
            }
          }, GroovyIntentionsBundle.message("create.parameter.for.field.intention.name"), null);
        }
      }).createPopup().showInBestPositionFor(editor);
  }

  private static void addParameter(final GrField selectedValue, final GrMethod constructor, final Project project) {
    List<GrParameterInfo> parameters = new ArrayList<GrParameterInfo>();
    GrParameter[] constructorParameters = constructor.getParameters();
    for (int i = 0; i < constructorParameters.length; i++) {
      parameters.add(new GrParameterInfo(constructorParameters[i], i));
    }
    final String[] suggestedNames =
      JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, selectedValue.getName(), null, null).names;

    final DefaultGroovyVariableNameValidator nameValidator =
      new DefaultGroovyVariableNameValidator(constructor, Collections.<String>emptyList(), false);
    String parameterName = ContainerUtil.find(suggestedNames, new Condition<String>() {
      @Override
      public boolean value(String name) {
        return nameValidator.validateName(name, false).length() > 0;
      }
    });

    if (parameterName == null) {
      parameterName = nameValidator.validateName(suggestedNames[0], true);
    }
    parameters.add(new GrParameterInfo(parameterName, "null", "", selectedValue.getTypeGroovy(), -1));

    PsiClassType[] exceptionTypes = constructor.getThrowsList().getReferencedTypes();
    ThrownExceptionInfo[] thrownExceptionInfos = new ThrownExceptionInfo[exceptionTypes.length];
    for (int i = 0; i < exceptionTypes.length; i++) {
      new JavaThrownExceptionInfo(i, exceptionTypes[i]);
    }

    final GrChangeInfoImpl grChangeInfo =
      new GrChangeInfoImpl(constructor, null, null, constructor.getName(), parameters, thrownExceptionInfos, false);
    final GrChangeSignatureProcessor processor = new GrChangeSignatureProcessor(project, grChangeInfo);
    final Ref<Boolean> success = Ref.create(Boolean.FALSE);
    processor.setPrepareSuccessfulSwingThreadCallback(new Runnable() {
      @Override
      public void run() {
        success.set(Boolean.TRUE);
      }
    });
    processor.run();

    if (success.get()) {
      final GrOpenBlock block = constructor.getBlock();
      LOG.assertTrue(block != null);
      final GrStatement statement = block.addStatementBefore(
        GroovyPsiElementFactory.getInstance(project).createStatementFromText(selectedValue.getName() + " = " + parameterName), null);
      final GrReferenceExpression ref = (GrReferenceExpression)((GrAssignmentExpression)statement).getLValue();
      if (!PsiManager.getInstance(project).areElementsEquivalent(ref.resolve(), selectedValue)) {
        PsiUtil.qualifyMemberReference(ref, selectedValue, selectedValue.getName());
      }
    }
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  static class MyPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      final List<GrField> candidates = findFieldCandidates(element);
      if (candidates != null && candidates.size() > 0) return true;
      final List<GrMethod> constructors = findConstructorCandidates(element);
      return constructors != null && constructors.size() > 0;
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
    return findCandidates(constructor, (GrTypeDefinition)clazz);
  }

  private static List<GrField> findCandidates(GrMethod constructor, final GrTypeDefinition clazz) {
    final List<GrField> usedFields = new ArrayList<GrField>();
    //ContainerUtil.addAll(fields, clazz.getFields());
    final GrOpenBlock block = constructor.getBlock();
    LOG.assertTrue(block != null);

    final PsiManager manager = clazz.getManager();
    block.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        final PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof GrField &&
            manager.areElementsEquivalent(((GrField)resolved).getContainingClass(), clazz) &&
            PsiUtil.isAccessedForWriting(referenceExpression)) {
          usedFields.add((GrField)resolved);
        }
      }

      @Override
      public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
      }

      @Override
      public void visitClosure(GrClosableBlock closure) {
      }
    });

    List<GrField> fields = new ArrayList<GrField>();
    for (final GrField field : clazz.getFields()) {
      if (field.getInitializerGroovy() != null) continue;
      if (ContainerUtil.find(usedFields, new Condition<PsiField>() {
        @Override
        public boolean value(PsiField o) {
          return manager.areElementsEquivalent(o, field);
        }
      }) == null) {
        fields.add(field);
      }
    }

    return fields;
  }

  @Nullable
  private static List<GrMethod> findConstructorCandidates(PsiElement element) {
    final GrField field = PsiTreeUtil.getParentOfType(element, GrField.class);
    if (field == null) return null;
    return findConstructorCandidates(field, (GrTypeDefinition)field.getContainingClass());
  }

  private static List<GrMethod> findConstructorCandidates(final GrField field, GrTypeDefinition psiClass) {
    final List<GrMethod> result = new ArrayList<GrMethod>();
    final PsiMethod[] constructors = psiClass.getConstructors();
    final PsiManager manager = field.getManager();
    for (PsiMethod constructor : constructors) {
      final List<GrField> fields = findCandidates(((GrMethod)constructor), psiClass);
      if (ContainerUtil.find(fields, new Condition<GrField>() {
        @Override
        public boolean value(GrField grField) {
          return manager.areElementsEquivalent(grField, field);
        }
      }) != null) {
        result.add((GrMethod)constructor);
      }
    }
    return result;
  }
}
