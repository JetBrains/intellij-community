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
package org.jetbrains.plugins.groovy.intentions.style.parameterToEntry;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.refactoring.GroovyValidationUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author ilyas
 */
public class ConvertParameterToMapEntryIntention extends Intention {

  private static final Logger LOG =
    Logger.getInstance("#org.jetbrains.plugins.groovy.intentions.style.ConvertParameterToMapEntryIntention");
  @NonNls private static final String CLOSURE_CAPTION = "closure";
  @NonNls private static final String CLOSURE_CAPTION_CAP = "Closure";
  @NonNls private static final String METHOD_CAPTION = "method";
  @NonNls private static final String METHOD_CAPTION_CAP = "Method";
  @NonNls private static final String REFACTORING_NAME = "Convert Parameter to Map Entry";
  @NonNls private static final String MAP_TYPE_TEXT = "Map";
  @NonNls private static final String[] MY_POSSIBLE_NAMES = new String[]{"attrs", "args", "params", "map"};

  @Override
  protected void processIntention(@NotNull final PsiElement element, final Project project, Editor editor) throws IncorrectOperationException {
    // Method or closure to be refactored
    final GrParametersOwner owner = PsiTreeUtil.getParentOfType(element, GrParametersOwner.class);
    final Collection<PsiElement> occurrences = new ArrayList<>();
    // Find all referenced expressions
    final boolean success = collectOwnerOccurrences(project, owner, occurrences);
    if (!success) return;
    // Checking for Groovy files only
    final boolean isClosure = owner instanceof GrClosableBlock;
    if (!checkOwnerOccurrences(project, occurrences, isClosure)) return;

    // To add or not to add new parameter for map entries
    final GrParameter firstParam = getFirstParameter(owner);

    switch (analyzeForNamedArguments(owner, occurrences)) {
      case ERROR: {
        final GrNamedElement namedElement = getReferencedElement(owner);
        LOG.assertTrue(namedElement != null);
        final String msg = GroovyIntentionsBundle
          .message("wrong.first.parameter.type", isClosure ? CLOSURE_CAPTION_CAP : METHOD_CAPTION_CAP, namedElement.getName(),
                   firstParam.getName());
        showErrorMessage(msg, project);
        return;
      }
      case MUST_BE_MAP: {
        if (firstParam == getAppropriateParameter(element)) {
          final String msg = GroovyIntentionsBundle.message("convert.cannot.itself");
          showErrorMessage(msg, project);
          return;
        }
        performRefactoring(element, owner, occurrences, false, null, false);
        break;
      }
      case IS_NOT_MAP: {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          final String[] possibleNames = generateValidNames(MY_POSSIBLE_NAMES, firstParam);

          final GroovyMapParameterDialog dialog = new GroovyMapParameterDialog(project, possibleNames, true) {
            @Override
            protected void doOKAction() {
              String name = getEnteredName();
              MultiMap<PsiElement, String> conflicts = new MultiMap<>();
              assert name != null;
              GroovyValidationUtil.validateNewParameterName(firstParam, conflicts, name);
              if (isClosure) {
                findClosureConflictUsages(conflicts, occurrences);
              }
              if (reportConflicts(conflicts, project)) {
                performRefactoring(element, owner, occurrences, createNewFirst(), name, specifyTypeExplicitly());
              }
              super.doOKAction();
            }
          };
          dialog.show();
        }
        else {
          //todo add statictics manager
          performRefactoring(element, owner, occurrences, true,
                             (new GroovyValidationUtil.ParameterNameSuggester("attrs", firstParam)).generateName(), true);
        }
        break;
      }
    }
  }

  private static void findClosureConflictUsages(MultiMap<PsiElement, String> conflicts,
                                                Collection<PsiElement> occurrences) {
    for (PsiElement occurrence : occurrences) {
      PsiElement origin = occurrence;
      while (occurrence instanceof GrReferenceExpression) {
        occurrence = occurrence.getParent();
      }
      if (occurrence instanceof GrArgumentList) {
        conflicts.putValue(origin, GroovyIntentionsBundle.message("closure.used.as.variable"));
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static String[] generateValidNames(final String[] names, final GrParameter param) {
    return ContainerUtil.map2Array(names, String.class, s -> (new GroovyValidationUtil.ParameterNameSuggester(s, param)).generateName());
  }

  private static void performRefactoring(final PsiElement element,
                                         final GrParametersOwner owner,
                                         final Collection<PsiElement> occurrences,
                                         final boolean createNewFirstParam,
                                         @Nullable final String mapParamName,
                                         final boolean specifyMapType) {
    final GrParameter param = getAppropriateParameter(element);
    assert param != null;
    final String paramName = param.getName();
    final String mapName = createNewFirstParam ? mapParamName : getFirstParameter(owner).getName();


    final Project project = element.getProject();
    final Runnable runnable = () -> {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      final GrParameterList list = owner.getParameterList();
      assert list != null;
      final int index = list.getParameterNumber(param);
      if (!createNewFirstParam && index <= 0) { // bad undo
        return;
      }

      //Remove old arguments from occurrences
      //final List<GrCall> calls = getCallOccurrences(occurrences);
      try {
        for (PsiElement occurrence : occurrences) {
          GrReferenceExpression refExpr = null;
          GroovyResolveResult resolveResult = null;
          boolean isExplicitGetterCall = false;
          if (occurrence instanceof GrReferenceExpression) {
            final PsiElement parent = occurrence.getParent();
            if (parent instanceof GrCall) {
              refExpr = (GrReferenceExpression)occurrence;
              resolveResult = refExpr.advancedResolve();
              final PsiElement resolved = resolveResult.getElement();
              if (resolved instanceof PsiMethod &&
                  GroovyPropertyUtils.isSimplePropertyGetter(((PsiMethod)resolved)) &&
                  //check for explicit getter call
                  ((PsiMethod)resolved).getName().equals(refExpr.getReferenceName())) {
                isExplicitGetterCall = true;
              }
            }
            else if (parent instanceof GrReferenceExpression) {
              resolveResult = ((GrReferenceExpression)parent).advancedResolve();
              final PsiElement resolved = resolveResult.getElement();
              if (resolved instanceof PsiMethod && "call".equals(((PsiMethod)resolved).getName())) {
                refExpr = (GrReferenceExpression)parent;
              }
            }
          }
          if (refExpr == null) continue;
          final GrClosureSignature signature = generateSignature(owner, refExpr);
          if (signature == null) continue;
          GrCall call;
          if (isExplicitGetterCall) {
            PsiElement parent = refExpr.getParent();
            LOG.assertTrue(parent instanceof GrCall);
            parent = parent.getParent();
            if (parent instanceof GrReferenceExpression && "call".equals(((GrReferenceExpression)parent).getReferenceName())) {
              parent = parent.getParent();
            }
            if (parent instanceof GrCall) {
              call = (GrCall)parent;
            }
            else {
              continue;
            }
          }
          else {
            call = (GrCall)refExpr.getParent();
          }

          if (resolveResult.isInvokedOnProperty()) {
            final PsiElement parent = call.getParent();
            if (parent instanceof GrCall) {
              call = (GrCall)parent;
            }
            else if (parent instanceof GrReferenceExpression && parent.getParent() instanceof GrCall) {
              final PsiElement resolved = ((GrReferenceExpression)parent).resolve();
              if (resolved instanceof PsiMethod && "call".equals(((PsiMethod)resolved).getName())) {
                call = (GrCall)parent.getParent();
              }
              else {
                continue;
              }
            }
          }

          final GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos = GrClosureSignatureUtil.mapParametersToArguments(signature, call);
          if (argInfos == null) continue;
          final GrClosureSignatureUtil.ArgInfo<PsiElement> argInfo = argInfos[index];

          final GrNamedArgument namedArg;
          if (argInfo.isMultiArg) {
            if (argInfo.args.isEmpty()) continue;
            String arg = "[" + StringUtil.join(ContainerUtil.map(argInfo.args, element1 -> element1.getText()), ", ") + "]";
            for (PsiElement psiElement : argInfo.args) {
              psiElement.delete();
            }
            namedArg = factory.createNamedArgument(paramName, factory.createExpressionFromText(arg));
          }
          else {
            if (argInfo.args.isEmpty()) continue;
            final PsiElement argument = argInfo.args.iterator().next();
            assert argument instanceof GrExpression;
            namedArg = factory.createNamedArgument(paramName, (GrExpression)argument);
            argument.delete();
          }
          call.addNamedArgument(namedArg);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      //Replace of occurrences of old parameter in closure/method
      final Collection<PsiReference> references = ReferencesSearch.search(param).findAll();
      for (PsiReference ref : references) {
        final PsiElement elt = ref.getElement();
        if (elt instanceof GrReferenceExpression) {
          GrReferenceExpression expr = (GrReferenceExpression)elt;
          final GrExpression newExpr = factory.createExpressionFromText(mapName + "." + paramName);
          expr.replaceWithExpression(newExpr, true);
        }
      }

      //Add new map parameter to closure/method if it's necessary
      if (createNewFirstParam) {
        try {
          final GrParameter newParam = factory.createParameter(mapName, specifyMapType ? MAP_TYPE_TEXT : "", null);
          list.addAfter(newParam, null);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      //Eliminate obsolete parameter from parameter list
      param.delete();
    };

    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(runnable), REFACTORING_NAME, null);
  }


  @Nullable
  private static GrParameter getAppropriateParameter(final PsiElement element) {
    if (element instanceof GrParameter) {
      return (GrParameter)element;
    }
    if (element instanceof GrReferenceExpression) {
      final GrReferenceExpression expr = (GrReferenceExpression)element;
      final PsiElement resolved = expr.resolve();
      LOG.assertTrue(resolved instanceof GrParameter);
      return ((GrParameter)resolved);
    }
    LOG.error("Selected expression is not resolved to method/closure parameter");
    return null;
  }

  @Nullable
  private static GrClosureSignature generateSignature(GrParametersOwner owner, GrReferenceExpression refExpr) {
    if (owner instanceof PsiMethod) {
      final GroovyResolveResult resolveResult = refExpr.advancedResolve();
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      return GrClosureSignatureUtil.createSignature((PsiMethod)owner, substitutor);
    }
    else if (owner instanceof GrClosableBlock) {
      return GrClosureSignatureUtil.createSignature((GrClosableBlock)owner);
    }
    return null;
  }

  /**
   * @param owner       Method or closure
   * @param occurrences references to owner
   * @return true if there we use owner's first parameter as map, false if we need to add ne one as fist map
   */
  private static FIRST_PARAMETER_KIND analyzeForNamedArguments(final GrParametersOwner owner, final Collection<PsiElement> occurrences) {
    boolean thereAreNamedArguments = false;
    for (PsiElement occurrence : occurrences) {
      if (occurrence instanceof GrReferenceExpression && occurrence.getParent() instanceof GrCall) {
        final GrCall call = (GrCall)occurrence.getParent();
        final GrArgumentList args = call.getArgumentList();
        if (args != null && args.getNamedArguments().length > 0) {
          thereAreNamedArguments = true;
        }
      }
      if (thereAreNamedArguments) break;
    }
    if (thereAreNamedArguments) {
      if (firstOwnerParameterMustBeMap(owner)) {
        return FIRST_PARAMETER_KIND.MUST_BE_MAP;
      }
      return FIRST_PARAMETER_KIND.ERROR;
    }
    return FIRST_PARAMETER_KIND.IS_NOT_MAP;
  }

  private static boolean firstOwnerParameterMustBeMap(final GrParametersOwner owner) {
    final GrParameter first = getFirstParameter(owner);
    final PsiType type = first.getTypeGroovy();
    final PsiClassType mapType = GrMapType.create(GlobalSearchScope.allScope(owner.getProject()));
    // First parameter may be used as map
    return type == null || type.isConvertibleFrom(mapType);
  }

  @NotNull
  private static GrParameter getFirstParameter(final GrParametersOwner owner) {
    final GrParameter[] params = owner.getParameters();
    LOG.assertTrue(params.length > 0);
    return params[0];
  }

  protected enum FIRST_PARAMETER_KIND {
    IS_NOT_MAP, MUST_BE_MAP, ERROR
  }

  @Nullable
  private static GrNamedElement getReferencedElement(final GrParametersOwner owner) {
    if (owner instanceof GrMethodImpl) return ((GrMethodImpl)owner);
    if (owner instanceof GrClosableBlock) {
      final PsiElement parent = owner.getParent();
      if (parent instanceof GrVariable && ((GrVariable)parent).getInitializerGroovy() == owner) return ((GrVariable)parent);
    }
    return null;
  }

  private static boolean checkOwnerOccurrences(final Project project, final Collection<PsiElement> occurrences, final boolean isClosure) {
    boolean result = true;
    final StringBuilder msg = new StringBuilder();
    msg.append(GroovyIntentionsBundle.message("conversion.not.allowed.in.non.groovy.files", isClosure ? CLOSURE_CAPTION : METHOD_CAPTION));
    for (PsiElement element : occurrences) {
      final PsiFile file = element.getContainingFile();
      if (!(file instanceof GroovyFileBase)) {
        result = false;
        msg.append("\n").append(file.getName());
      }
    }
    if (!result) {
      showErrorMessage(msg.toString(), project);
      return false;
    }
    return true;
  }

  private static boolean collectOwnerOccurrences(final Project project,
                                                 final GrParametersOwner owner,
                                                 final Collection<PsiElement> occurrences) {
    final PsiElement namedElem = getReferencedElement(owner);
    if (namedElem == null) return true;
    final Ref<Boolean> result = new Ref<>(true);
    final Task task = new Task.Modal(project, GroovyIntentionsBundle
      .message("find.method.ro.closure.usages.0", owner instanceof GrClosableBlock ? CLOSURE_CAPTION : METHOD_CAPTION), true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        final Collection<PsiReference> references = Collections.synchronizedSet(new HashSet<PsiReference>());
        final Processor<PsiReference> consumer = psiReference -> {
          references.add(psiReference);
          return true;
        };
        ReferencesSearch.search(namedElem).forEach(consumer);
        boolean isProperty = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            return namedElem instanceof GrField && ((GrField)namedElem).isProperty();
          }
        });
        if (isProperty) {
          final GrAccessorMethod[] getters = ApplicationManager.getApplication().runReadAction(new Computable<GrAccessorMethod[]>() {
            @Override
            public GrAccessorMethod[] compute() {
              return ((GrField)namedElem).getGetters();
            }
          });
          for (GrAccessorMethod getter : getters) {
            MethodReferencesSearch.search(getter).forEach(consumer);
          }
        }
        for (final PsiReference reference : references) {
          ApplicationManager.getApplication().runReadAction(() -> {
            final PsiElement element = reference.getElement();
            if (element != null) {
              occurrences.add(element);
            }
          });
        }
      }

      @Override
      public void onCancel() {
        result.set(false);
      }

      @Override
      public void onError(@NotNull Exception error) {
        super.onError(error);
        result.set(false);
      }

      @Override
      public void onSuccess() {
        result.set(true);
      }
    };
    ProgressManager.getInstance().run(task);
    return result.get().booleanValue();
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new MyPsiElementPredicate();
  }

  private static class MyPsiElementPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(final PsiElement element) {
      GrParameter parameter = null;
      if (element instanceof GrParameter) {
        parameter = (GrParameter)element;
      }
      else if (element instanceof GrReferenceExpression) {
        GrReferenceExpression expr = (GrReferenceExpression)element;
        if (expr.getQualifierExpression() != null) return false;
        final PsiElement resolved = expr.resolve();
        if (resolved instanceof GrParameter) {
          parameter = (GrParameter)resolved;
        }
      }
      if (parameter == null) return false;
      if (parameter.isOptional()) return false;

      GrParametersOwner owner = PsiTreeUtil.getParentOfType(element, GrParametersOwner.class);
      if (!(owner instanceof GrClosableBlock || owner instanceof GrMethod)) return false;
      return checkForMapParameters(owner);
    }
  }

  private static boolean checkForMapParameters(GrParametersOwner owner) {
    final GrParameter[] parameters = owner.getParameters();
    if (parameters.length != 1) return true;

    final GrParameter parameter = parameters[0];
    final PsiType type = parameter.getTypeGroovy();
    if (!(type instanceof PsiClassType)) return true;

    final PsiClass psiClass = ((PsiClassType)type).resolve();
    return psiClass == null || !CommonClassNames.JAVA_UTIL_MAP.equals(psiClass.getQualifiedName());
  }

  private static void showErrorMessage(String message, final Project project) {
    CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
  }

  private static boolean reportConflicts(final MultiMap<PsiElement, String> conflicts, final Project project) {
    if (conflicts.isEmpty()) return true;
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    return conflictsDialog.showAndGet();
  }
}
