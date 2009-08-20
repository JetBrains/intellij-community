package org.jetbrains.plugins.groovy.gant;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.gant.GantIcons;
import org.jetbrains.plugins.groovy.gant.GantUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author ilyas
 */
public class GantTargetReference implements PsiPolyVariantReference {

  @NotNull
  private final GrReferenceExpression myRefExpr;

  public GantTargetReference(@NotNull GrReferenceExpression refExpr) {
    myRefExpr = refExpr;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    if (myRefExpr.getQualifierExpression() != null) return ResolveResult.EMPTY_ARRAY;

    final PsiElement parent = myRefExpr.getParent();
    if (!(parent instanceof GrCall)) return ResolveResult.EMPTY_ARRAY;

    PsiFile file = myRefExpr.getContainingFile();
    if (!GantUtils.isGantScriptFile(file)) return ResolveResult.EMPTY_ARRAY;

    ArrayList<ResolveResult> res = new ArrayList<ResolveResult>();
    final GroovyFile groovyFile = (GroovyFile)file;
    for (GrArgumentLabel label : GantUtils.getScriptTargets(groovyFile)) {
      if (label.getName().equals(myRefExpr.getName())) {
        res.add(new GroovyResolveResultImpl(label, true));
      }
    }

    // Add ant tasks
    final Project project = groovyFile.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myRefExpr.getProject());
    final PsiClass taskClass = facade.findClass(AntTasksProvider.ANT_TASK_CLASS, GlobalSearchScope.allScope(project));

    final String name = myRefExpr.getName();
    if (name != null && taskClass != null) {
      final Set<String> tasks = AntTasksProvider.getInstance(project).getAntTasks();
      final String capitalized = StringUtil.capitalize(name);
      if (tasks.contains(capitalized)) {
        final PsiClass[] classes = facade.getShortNamesCache().getClassesByName(capitalized, GlobalSearchScope.allScope(project));
        for (PsiClass clazz : classes) {
          if (clazz.isInheritor(taskClass, true)) {
            res.add(new GroovyResolveResultImpl(clazz, true));
          }
        }
      }
    }

    return res.toArray(new ResolveResult[res.size()]);
  }

  @NotNull
  public GrReferenceExpression getElement() {
    return myRefExpr;
  }

  public TextRange getRangeInElement() {
    return getElement().getRangeInElement();
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    if (results.length == 1) return results[0].getElement();
    return null;
  }

  public String getCanonicalText() {
    return myRefExpr.getCanonicalText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return myRefExpr.setName(newElementName);
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return false;
  }

  public LookupElement[] getScriptTargets() {
    List<LookupElement> pageProperties = new ArrayList<LookupElement>();
    final LookupElementFactory factory = LookupElementFactory.getInstance();
    PsiFile file = myRefExpr.getContainingFile();
    if (GantUtils.isGantScriptFile(file)) {
      final GroovyFile groovyFile = (GroovyFile)file;
      for (GrArgumentLabel label : GantUtils.getScriptTargets(groovyFile)) {
        String name = label.getName();
        pageProperties.add(factory.createLookupElement(name).setIcon(GantIcons.GANT_TASK));
      }
      for (String taskName : AntTasksProvider.getInstance(file.getProject()).getAntTasks()) {
        final String name = StringUtil.decapitalize(taskName);
        pageProperties.add(factory.createLookupElement(name).setIcon(GantIcons.ANT_TASK));
      }
    }
    return pageProperties.toArray(new LookupElement[pageProperties.size()]);
  }


  public Object[] getVariants() {
    GrExpression qualifier = myRefExpr.getQualifierExpression();
    if (qualifier == null && !(myRefExpr.getParent() instanceof GrReferenceExpression)) {
      return getScriptTargets();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

}


