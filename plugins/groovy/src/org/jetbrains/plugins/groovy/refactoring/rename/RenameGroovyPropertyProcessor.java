package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author ilyas
 */
public class RenameGroovyPropertyProcessor extends RenameJavaVariableProcessor {

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(final PsiElement element) {
    ArrayList<PsiReference> refs = new ArrayList<PsiReference>();
    if (element instanceof GrField) {
      GrField field = (GrField)element;
      PsiMethod setter = GroovyPropertyUtils.findSetterForField(field);
      GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
      if (setter != null && setter instanceof GrAccessorMethod) {
        refs.addAll(MethodReferencesSearch.search(setter, projectScope, true).findAll());
      }
      GrAccessorMethod[] getters = field.getGetters();
      for (GrAccessorMethod getter : getters) {
        refs.addAll(MethodReferencesSearch.search(getter, projectScope, true).findAll());
      }
      return refs;
    }
    return super.findReferences(element);
  }

  @Override
  public void renameElement(final PsiElement psiElement,
                            final String newName,
                            final UsageInfo[] usages,
                            final RefactoringElementListener listener) throws IncorrectOperationException {

    final GrField field = (GrField)psiElement;
    final PsiMethod getter = GroovyPropertyUtils.findGetterForField(field);
    final PsiMethod setter = GroovyPropertyUtils.findSetterForField(field);
    final String newGetterName = (getter != null && getter.getName().startsWith("is") ? "is" : "get") + StringUtil.capitalize(newName);
    final String newSetterName = "set" + StringUtil.capitalize(newName);

    // rename all references
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element == null) {
        continue;
      } else {
        PsiReference ref = element.getReference();
        if (ref != null) {
          PsiElement resolved = ref.resolve();
          if (resolved instanceof GrAccessorMethod) {
            GrAccessorMethod method = (GrAccessorMethod)resolved;
            if (method == getter) {

              ref.handleElementRename(newGetterName);
            } else {
              if (method == setter) {

                ref.handleElementRename(newSetterName);
              }
            }
          } else {
            ref.handleElementRename(newName);
          }

        }
      }
    }
    // do actual rename
    field.setName(newName);
    listener.elementRenamed(field);
  }

  @Override
  public boolean canProcessElement(final PsiElement element) {
    return element instanceof GrField && ((GrField)element).isProperty();
  }

  @Override
  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    GrField field = (GrField)element;
    final PsiMethod getter = GroovyPropertyUtils.findGetterForField(field);
    final PsiMethod setter = GroovyPropertyUtils.findSetterForField(field);
    if (getter != null && !(getter instanceof GrAccessorMethod) || setter != null && !(setter instanceof GrAccessorMethod)) {
      if (askToRenameAccesors(getter, setter, newName, element.getProject())) {
        if (getter != null && !(getter instanceof GrAccessorMethod)) {
          String name = getter.getName();
          allRenames.put(getter, name.startsWith("is") ? "is" + StringUtil.capitalize(newName) : "get" + StringUtil.capitalize(newName));
        }
        if (setter != null && !(setter instanceof GrAccessorMethod)) {
          allRenames.put(setter, "set" + StringUtil.capitalize(newName));
        }
      }
    }
  }

  private static boolean askToRenameAccesors(PsiMethod getter, PsiMethod setter, String newName, final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false;
    String text = RefactoringMessageUtil.getGetterSetterMessage(newName, RefactoringBundle.message("rename.title"), getter, setter);
    return Messages.showYesNoDialog(project, text, RefactoringBundle.message("rename.title"), Messages.getQuestionIcon()) == 0;
  }


}
