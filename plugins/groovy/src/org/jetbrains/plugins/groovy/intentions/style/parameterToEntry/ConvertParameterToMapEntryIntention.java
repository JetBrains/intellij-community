package org.jetbrains.plugins.groovy.intentions.style.parameterToEntry;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import static org.jetbrains.plugins.groovy.intentions.style.parameterToEntry.ConvertParameterToMapEntryIntention.FIRST_PARAMETER_KIND.*;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public class ConvertParameterToMapEntryIntention extends Intention {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.intentions.style.ConvertParameterToMapEntryIntention")
      ;

  @NonNls private static final String CLOSURE_CAPTION = "closure";
  @NonNls private static final String CLOSURE_CAPTION_CAP = "Closure";
  @NonNls private static final String METHOD_CAPTION = "method";
  @NonNls private static final String METHOD_CAPTION_CAP = "Method";
  @NonNls private static final String REFACTORING_NAME = "Convert Parameter to Map Entry";

  protected void processIntention(@NotNull final PsiElement element) throws IncorrectOperationException {
    final Project project = element.getProject();
    // Method or closure to be refactored
    final GrParametersOwner owner = PsiTreeUtil.getParentOfType(element, GrParametersOwner.class);
    final Collection<PsiElement> occurrences = new ArrayList<PsiElement>();
    // Find all referenced expressions
    final boolean success = collectOwnerOccurrences(project, owner, occurrences);
    if (!success) return;
    // Checking for Groovy files only
    final boolean isClosure = owner instanceof GrClosableBlock;
    if (!checkOwnerOccurences(project, occurrences, isClosure)) return;

    // To add or not to add new parameter for map entries
    final GrParameter fisrtParam = getFirstParameter(owner);
    final PsiReference ref = element.getReference();
    switch (analyzeForNamedArguments(owner, occurrences)) {
      case ERROR: {
        final GrNamedElement namedElement = getReferencedElement(owner);
        LOG.assertTrue(namedElement != null);
        final String msg = GroovyIntentionsBundle
            .message("wrong.first.parameter.type", isClosure ? CLOSURE_CAPTION_CAP : METHOD_CAPTION_CAP, namedElement.getName(),
                     fisrtParam.getName());
        showErrorMessage(msg, project);
        return;
      }
      case MUST_BE_MAP: {
        if (element == fisrtParam || ref != null && ref.resolve() == fisrtParam) {
          final String msg = GroovyIntentionsBundle.message("convert.cannot.itself");
          showErrorMessage(msg, project);
          return;
        }
        performRefactoring(owner, occurrences, false);
        break;
      }
      case NOT_MAP: {
        performRefactoring(owner, occurrences, true);
        break;
      }
      case MAY_BE_MAP: {
        if (!(element == fisrtParam || ref != null && ref.resolve() == fisrtParam)) {
          final FirstParameterDialog dialog = new FirstParameterDialog();
          dialog.show();
          if (dialog.isOK()) {
            performRefactoring(owner, occurrences, !dialog.createNewFirst());
          }
        }
        else {
          performRefactoring(owner, occurrences, true);
        }
        break;
      }
    }
  }

  private void performRefactoring(final GrParametersOwner owner,
                                  final Collection<PsiElement> occurrences,
                                  final boolean createNewFirstParam) {
    /*todo implement
    replace all occurrences in method
    refactor all references
    */
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
        return MUST_BE_MAP;
      }
      return ERROR;
    }
    else if (firstOwnerParameterMustBeMap(owner)) {
      return MAY_BE_MAP;
    }
    return NOT_MAP;
  }

  private static boolean firstOwnerParameterMustBeMap(final GrParametersOwner owner) {
    final GrParameter first = getFirstParameter(owner);
    final PsiType type = first.getTypeGroovy();
    final PsiClassType mapType = PsiUtil.getMapType(owner.getManager(), GlobalSearchScope.allScope(owner.getProject()));
    // First parameter may be used as map
    return type == null || type.isAssignableFrom(mapType);
  }

  private static GrParameter getFirstParameter(final GrParametersOwner owner) {
    final GrParameter[] params = owner.getParameters();
    LOG.assertTrue(params.length > 0);
    final GrParameter first = params[0];
    return first;
  }

  private static boolean firstParamHasOccurrences(final GrParametersOwner owner) {
    final GrParameter first = getFirstParameter(owner);
    return ReferencesSearch.search(first).findAll().size() > 0;
  }

  protected static enum FIRST_PARAMETER_KIND {
    NOT_MAP, MAY_BE_MAP, MUST_BE_MAP, ERROR
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

  private static boolean checkOwnerOccurences(final Project project, final Collection<PsiElement> occurrences, final boolean isClosure) {
    boolean result = true;
    final StringBuffer msg = new StringBuffer();
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
    final Ref<Boolean> result = new Ref<Boolean>(true);
    final Task task = new Task.Modal(project, GroovyIntentionsBundle.message("find.method.ro.closure.usages.0",
                                                                             owner instanceof GrClosableBlock
                                                                             ? CLOSURE_CAPTION
                                                                             : METHOD_CAPTION), true) {
      public void run(@NotNull final ProgressIndicator indicator) {
        final Query<PsiReference> query = ReferencesSearch.search(namedElem);
        final Collection<PsiReference> references = query.findAll();
        for (PsiReference reference : references) {
          final PsiElement element = reference.getElement();
          if (element != null) {
            occurrences.add(element);
          }
        }
      }

      @Override
      public void onCancel() {
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

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new MyPsiElementPredicate();
  }

  private static class MyPsiElementPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(final PsiElement element) {
      if (element instanceof GrParameter) {
        final GrParametersOwner owner = PsiTreeUtil.getParentOfType(element, GrParametersOwner.class);
        if (owner instanceof GrClosableBlock || owner instanceof GrMethodImpl) return true;
        return false;
      }

      if (element instanceof GrReferenceExpression) {
        GrReferenceExpression expr = (GrReferenceExpression)element;
        if (expr.getQualifierExpression() != null) return false;
        final PsiElement resolved = expr.resolve();
        if (!(resolved instanceof GrParameter)) return false;
        final GrParametersOwner owner = PsiTreeUtil.getParentOfType(resolved, GrParametersOwner.class);
        if (owner instanceof GrClosableBlock || owner instanceof GrMethodImpl) return true;
      }
      return false;
    }
  }

  private static void showErrorMessage(String message, final Project project) {
    CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
  }
}
