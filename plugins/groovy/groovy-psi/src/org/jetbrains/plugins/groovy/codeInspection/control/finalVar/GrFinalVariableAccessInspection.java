// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.control.finalVar;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GrFieldControlFlowPolicy;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.transformations.immutable.GrImmutableUtils;

import java.util.*;

/**
 * @author Max Medvedev
 */
public class GrFinalVariableAccessInspection extends BaseInspection {
  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethod(@NotNull GrMethod method) {
        super.visitMethod(method);

        final GrOpenBlock block = method.getBlock();
        if (block != null) {
          processLocalVars(block);
        }

        if (method.isConstructor()) {
          processFieldsInConstructors(method);
        }
      }

      @Override
      public void visitFile(@NotNull GroovyFileBase file) {
        super.visitFile(file);

        if (file instanceof GroovyFile && file.isScript()) {
          processLocalVars(file);
        }
      }

      @Override
      public void visitField(@NotNull GrField field) {
        super.visitField(field);

        final GrExpression initializer = field.getInitializerGroovy();
        if (initializer != null) {
          processLocalVars(initializer);
        }

        if (field.hasModifierProperty(PsiModifier.FINAL)) {
          if (!isFieldInitialized(field)) {
            registerError(field.getNameIdentifierGroovy(),
                          GroovyBundle.message("variable.0.might.not.have.been.initialized", field.getName()), LocalQuickFix.EMPTY_ARRAY,
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        }
      }

      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression ref) {
        super.visitReferenceExpression(ref);

        final PsiElement resolved = ref.resolve();
        if (resolved instanceof GrField && ((GrField)resolved).hasModifierProperty(PsiModifier.FINAL)) {
          final GrField field = (GrField)resolved;
          final PsiClass containingClass = field.getContainingClass();

          if (PsiUtil.isLValue(ref)) {
            if (containingClass == null || !PsiTreeUtil.isAncestor(containingClass, ref, true)) {
              registerError(ref, GroovyBundle.message("cannot.assign.a.value.to.final.field.0", field.getName()), LocalQuickFix.EMPTY_ARRAY,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
          }
          else if (PsiUtil.isUsedInIncOrDec(ref)) {
            if (containingClass == null || !isInsideConstructorOrInitializer(containingClass, ref, field.hasModifierProperty(PsiModifier.STATIC))) {
              registerError(ref, GroovyBundle.message("cannot.assign.a.value.to.final.field.0", field.getName()), LocalQuickFix.EMPTY_ARRAY,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
          }
        }
        else if (resolved instanceof GrParameter &&
                 ((GrParameter)resolved).getDeclarationScope() instanceof GrMethod &&
                 ((GrParameter)resolved).hasModifierProperty(PsiModifier.FINAL) &&
                 PsiUtil.isUsedInIncOrDec(ref)) {
          registerError(ref, GroovyBundle.message("cannot.assign.a.value.to.final.parameter.0", ((GrParameter)resolved).getName()),
                        LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }

      @Override
      public void visitClassInitializer(@NotNull GrClassInitializer initializer) {
        super.visitClassInitializer(initializer);

        processLocalVars(initializer.getBlock());
        processFieldsInClassInitializer(initializer);
      }

      private void processFieldsInConstructors(@NotNull GrMethod constructor) {
        final GrOpenBlock block = constructor.getBlock();
        if (block == null) return;

        final GrTypeDefinition clazz = (GrTypeDefinition)constructor.getContainingClass();
        if (clazz == null) return;

        final GrClassInitializer[] initializers = clazz.getInitializers();
        final List<GrField> fields = getFinalFields(clazz);

        Set<GrVariable> initializedFields = ContainerUtil.newHashSet();
        appendFieldInitializedInDeclaration(false, fields, initializedFields);
        appendFieldsInitializedInClassInitializer(initializers, null, false, fields, initializedFields);
        appendInitializationFromChainedConstructors(constructor, fields, initializedFields);

        final Instruction[] flow = buildFlowForField(block);
        final Map<String, GrVariable> variables = buildVarMap(fields, false);

        highlightInvalidWriteAccess(flow, variables, initializedFields);

      }

      private void processFieldsInClassInitializer(@NotNull GrClassInitializer initializer) {
        final GrTypeDefinition clazz = (GrTypeDefinition)initializer.getContainingClass();
        if (clazz == null) return;

        final boolean isStatic = initializer.isStatic();

        final GrClassInitializer[] initializers = clazz.getInitializers();
        final List<GrField> fields = getFinalFields(clazz);

        Set<GrVariable> initializedFields = ContainerUtil.newHashSet();
        appendFieldInitializedInDeclaration(isStatic, fields, initializedFields);
        appendFieldsInitializedInClassInitializer(initializers, initializer, isStatic, fields, initializedFields);

        final Instruction[] flow = buildFlowForField(initializer.getBlock());
        final Map<String, GrVariable> variables = buildVarMap(fields, isStatic);
        highlightInvalidWriteAccess(flow, variables, initializedFields);
      }

      private void processLocalVars(@NotNull GroovyPsiElement scope) {
        final MultiMap<PsiElement, GrVariable> scopes = collectVariables(scope);

        for (final Map.Entry<PsiElement, Collection<GrVariable>> entry : scopes.entrySet()) {
          final PsiElement scopeToProcess = entry.getKey();

          final Set<GrVariable> forInParameters = ContainerUtil.newHashSet();
          final Map<String, GrVariable> variables = ContainerUtil.newHashMap();
          for (final GrVariable var : entry.getValue()) {
            variables.put(var.getName(), var);
            if (var instanceof GrParameter && ((GrParameter)var).getDeclarationScope() instanceof GrForStatement) {
              forInParameters.add(var);
            }
          }

          final Instruction[] flow = getFlow(scopeToProcess);
          highlightInvalidWriteAccess(flow, variables, forInParameters);
        }
      }

      private void highlightInvalidWriteAccess(@NotNull Instruction[] flow,
                                               @NotNull Map<String, GrVariable> variables,
                                               @NotNull Set<GrVariable> initializedVariables) {
        final List<ReadWriteVariableInstruction> result =
          InvalidWriteAccessSearcher.findInvalidWriteAccess(flow, variables, initializedVariables);

        if (result == null) return;

        for (final ReadWriteVariableInstruction instruction : result) {
          if (variables.containsKey(instruction.getVariableName())) {
            registerError(instruction.getElement(),
                          GroovyBundle.message("cannot.assign.a.value.to.final.field.0", instruction.getVariableName()),
                          LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        }
      }
    };
  }

  private static boolean isInsideConstructorOrInitializer(@NotNull PsiClass containingClass, @NotNull GrReferenceExpression place, boolean isStatic) {
    PsiElement container = ControlFlowUtils.findControlFlowOwner(place);

    PsiClass aClass = null;
    if (!isStatic && container instanceof GrMethod && ((GrMethod)container).isConstructor()) {
      aClass = ((GrMethod)container).getContainingClass();
    }
    else if (container instanceof GrClassInitializer && ((GrClassInitializer)container).isStatic() == isStatic) {
      aClass = ((GrClassInitializer)container).getContainingClass();
    }
    return aClass != null && containingClass.getManager().areElementsEquivalent(aClass, containingClass);
  }

  @NotNull
  private static List<GrField> getFinalFields(@NotNull GrTypeDefinition clazz) {
    final GrField[] fields = clazz.getCodeFields();
    return ContainerUtil.filter(fields, field -> {
      final GrModifierList list = field.getModifierList();
      return list != null && list.hasModifierProperty(PsiModifier.FINAL);
    });
  }

  private static void appendFieldInitializedInDeclaration(boolean isStatic,
                                                          @NotNull List<GrField> fields,
                                                          @NotNull Set<GrVariable> initializedFields) {
    for (GrField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC) == isStatic && field.getInitializerGroovy() != null) {
        initializedFields.add(field);
      }
    }
  }

  private static void appendFieldsInitializedInClassInitializer(@NotNull GrClassInitializer[] initializers,
                                                                @Nullable GrClassInitializer initializerToStop,
                                                                boolean isStatic,
                                                                @NotNull List<GrField> fields,
                                                                @NotNull Set<GrVariable> initializedFields) {
    for (GrClassInitializer curInit : initializers) {
      if (curInit.isStatic() != isStatic) continue;
      if (curInit == initializerToStop) break;

      final GrOpenBlock block = curInit.getBlock();
      final Instruction[] flow = buildFlowForField(block);

      for (GrField field : fields) {
        if (field.hasModifierProperty(PsiModifier.STATIC) == isStatic &&
            !initializedFields.contains(field) &&
            VariableInitializationChecker.isVariableDefinitelyInitializedCached(field, block, flow)) {
          initializedFields.add(field);
        }
      }
    }
  }

  private static void appendInitializationFromChainedConstructors(@NotNull GrMethod constructor,
                                                                  @NotNull List<GrField> fields,
                                                                  @NotNull Set<GrVariable> initializedFields) {
    final List<GrMethod> chained = getChainedConstructors(constructor);
    chained.remove(0);

    for (GrMethod method : chained) {
      final GrOpenBlock block = method.getBlock();
      if (block == null) continue;

      final Instruction[] flow = buildFlowForField(block);

      for (GrField field : fields) {
        if (!field.hasModifierProperty(PsiModifier.STATIC) &&
            !initializedFields.contains(field) &&
            VariableInitializationChecker.isVariableDefinitelyInitializedCached(field, block, flow)) {
          initializedFields.add(field);
        }
      }
    }
  }

  @NotNull
  private static Map<String, GrVariable> buildVarMap(@NotNull List<GrField> fields, boolean isStatic) {
    Map<String, GrVariable> result = ContainerUtil.newHashMap();
    for (GrField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC) == isStatic) {
        result.put(field.getName(), field);
      }
    }
    return result;
  }

  private static boolean isFieldInitialized(@NotNull GrField field) {
    if (field instanceof GrEnumConstant) return true;
    if (field.getInitializerGroovy() != null) return true;

    if (isImmutableField(field)) return true;

    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);

    final GrTypeDefinition aClass = ((GrTypeDefinition)field.getContainingClass());
    if (aClass == null) return true;

    GrClassInitializer[] initializers = aClass.getInitializers();
    for (GrClassInitializer initializer : initializers) {
      if (initializer.isStatic() != isStatic) continue;

      final GrOpenBlock block = initializer.getBlock();
      final Instruction[] initializerFlow = buildFlowForField(block);
      if (VariableInitializationChecker.isVariableDefinitelyInitializedCached(field, block, initializerFlow)) {
        return true;
      }
    }

    if (isStatic) return false;

    final GrMethod[] constructors = aClass.getCodeConstructors();
    if (constructors.length == 0) return false;

    Set<GrMethod> initializedConstructors = ContainerUtil.newHashSet();
    Set<GrMethod> notInitializedConstructors = ContainerUtil.newHashSet();

    NEXT_CONSTR:
    for (GrMethod constructor : constructors) {
      if (constructor.getBlock() == null) return false;
      final List<GrMethod> chained = getChainedConstructors(constructor);

      NEXT_CHAINED:
      for (GrMethod method : chained) {
        if (initializedConstructors.contains(method)) {
          continue NEXT_CONSTR;
        }
        else if (notInitializedConstructors.contains(method)) {
          continue NEXT_CHAINED;
        }

        final GrOpenBlock block = method.getBlock();
        assert block != null;
        final boolean initialized =
          VariableInitializationChecker.isVariableDefinitelyInitializedCached(field, block, buildFlowForField(block));

        if (initialized) {
          initializedConstructors.add(method);
          continue NEXT_CONSTR;
        }
        else {
          notInitializedConstructors.add(method);
        }
      }

      return false;
    }
    return true;
  }

  private static boolean isImmutableField(@NotNull GrField field) {
    GrModifierList fieldModifierList = field.getModifierList();
    if (fieldModifierList != null && fieldModifierList.hasExplicitVisibilityModifiers()) return false;

    PsiClass aClass = field.getContainingClass();
    if (aClass == null) return false;

    return GrImmutableUtils.hasImmutableAnnotation(aClass);
  }

  @NotNull
  private static List<GrMethod> getChainedConstructors(@NotNull GrMethod constructor) {
    final HashSet<Object> visited = ContainerUtil.newHashSet();

    final ArrayList<GrMethod> result = ContainerUtil.newArrayList(constructor);
    while (true) {
      final GrConstructorInvocation invocation = PsiUtil.getConstructorInvocation(constructor);
      if (invocation != null && invocation.isThisCall()) {
        final PsiMethod method = invocation.resolveMethod();
        if (method != null && method.isConstructor() && visited.add(method)) {
          result.add((GrMethod)method);
          constructor = (GrMethod)method;
          continue;
        }
      }
      return result;
    }
  }

  @NotNull
  private static Instruction[] buildFlowForField(@NotNull GrOpenBlock block) {
    return new ControlFlowBuilder(block.getProject(), GrFieldControlFlowPolicy.getInstance()).buildControlFlow(block);
  }


  /**
   * @return map: scope -> variables defined in the scope
   */
  @NotNull
  private static MultiMap<PsiElement, GrVariable> collectVariables(@NotNull GroovyPsiElement scope) {
    final MultiMap<PsiElement, GrVariable> scopes = MultiMap.create();
    scope.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitVariable(@NotNull GrVariable variable) {
        super.visitVariable(variable);
        if (!(variable instanceof PsiField) && variable.hasModifierProperty(PsiModifier.FINAL)) {
          final PsiElement varScope = findScope(variable);
          if (varScope != null) {
            scopes.putValue(varScope, variable);
          }
        }
      }
    });
    return scopes;
  }

  @NotNull
  private static Instruction[] getFlow(@NotNull PsiElement element) {
    return element instanceof GrControlFlowOwner
           ? ((GrControlFlowOwner)element).getControlFlow()
           : new ControlFlowBuilder(element.getProject()).buildControlFlow((GroovyPsiElement)element);
  }


  @Nullable
  private static PsiElement findScope(@NotNull GrVariable variable) {
    GroovyPsiElement result = PsiTreeUtil.getParentOfType(variable, GrControlStatement.class, GrControlFlowOwner.class);
    if (result instanceof GrForStatement) {
      final GrStatement body = ((GrForStatement)result).getBody();
      if (body != null) {
        result = body;
      }
    }
    return result;
  }
}
