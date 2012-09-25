/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.other;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.PreferrableNameSuggestionProvider;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrAliasImportIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    GrReferenceExpression ref = (GrReferenceExpression)element;

    GroovyResolveResult result = ref.advancedResolve();
    GrImportStatement context = (GrImportStatement)result.getCurrentFileResolveContext();
    assert context != null;
    final PsiMember resolved = (PsiMember)result.getElement();

    assert resolved != null;
    doRefactoring(project, context, resolved);
  }

  private static void doRefactoring(@NotNull Project project, @NotNull GrImportStatement importStatement, @NotNull PsiMember member) {
    if (member instanceof GrAccessorMethod) member = ((GrAccessorMethod)member).getProperty();

    final GroovyFileBase file = (GroovyFileBase)importStatement.getContainingFile();
    final List<UsageInfo> usages = findUsages(member, file);
    GrImportStatement templateImport = createTemplateImport(project, importStatement, member, file);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      updateRefs(file, templateImport.getTextRange(), usages, member.getName());
    }
    else {
      runTemplate(project, importStatement, member, file, usages, templateImport);
    }
  }

  private static GrImportStatement createTemplateImport(Project project,
                                                        GrImportStatement context,
                                                        PsiMember resolved,
                                                        GroovyFileBase file) {
    final PsiClass aClass = resolved.getContainingClass();
    assert aClass != null;
    String qname = aClass.getQualifiedName();
    final String name = resolved.getName();

    GrImportStatement template = GroovyPsiElementFactory.getInstance(project)
      .createImportStatementFromText("import static " + qname + "." + name + " as aliased");
    GrImportStatement templateImport = file.addImport(template);
    if (!context.isOnDemand()) {
      context.delete();
    }
    return templateImport;
  }

  private static void runTemplate(Project project,
                                  GroovyPsiElement context,
                                  PsiMember resolved,
                                  final GroovyFileBase file,
                                  final List<UsageInfo> usages,
                                  GrImportStatement templateImport) {
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();

    TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(templateImport);

    LinkedHashSet<String> names = getSuggestedNames(resolved, context);

    final PsiElement aliasNameElement = templateImport.getAliasNameElement();
    assert aliasNameElement != null;
    templateBuilder.replaceElement(aliasNameElement,
                                   new MyLookupExpression(resolved.getName(), names, (PsiNamedElement)resolved, true, null));
    Template built = templateBuilder.buildTemplate();

    final Editor newEditor = QuickfixUtil.positionCursor(project, file, templateImport);
    final TextRange range = templateImport.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    final String name = resolved.getName();

    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(newEditor, built, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        if (brokenOff) return;

        updateRefs(file, range, usages, name);
      }
    });
  }

  private static void updateRefs(GroovyFileBase file, TextRange range, List<UsageInfo> usages, String memberName) {
    GrImportStatement updatedImport = PsiTreeUtil.findElementOfClassAtOffset(file, range.getStartOffset(), GrImportStatement.class, true);

    if (updatedImport == null) return;

    String name = updatedImport.getImportedName();

    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement == null) continue;

      if (usageElement.getParent() instanceof GrImportStatement) continue;

      if (usageElement instanceof GrReferenceElement) {
        final PsiElement qualifier = ((GrReferenceElement)usageElement).getQualifier();

        if (qualifier == null) {
          final String refName = ((GrReferenceElement)usageElement).getReferenceName();
          if (refName == null) continue;

          if (memberName.equals(refName)) {
            ((GrReferenceElement)usageElement).handleElementRenameSimple(name);
          }
          else if (refName.equals(GroovyPropertyUtils.getPropertyNameByAccessorName(memberName))) {
            final String newPropName = GroovyPropertyUtils.getPropertyNameByAccessorName(name);
            if (newPropName != null) {
              ((GrReferenceElement)usageElement).handleElementRenameSimple(newPropName);
            }
            else {
              ((GrReferenceElement)usageElement).handleElementRenameSimple(name);
            }
          }
        }
      }
    }
  }

  private static List<UsageInfo> findUsages(PsiMember member, GroovyFileBase file) {
    LocalSearchScope scope = new LocalSearchScope(file);

    Query<PsiReference> query;
    if (member instanceof PsiMethod) {
      query = MethodReferencesSearch.search((PsiMethod)member, scope, false);
    }
    else {
      query = ReferencesSearch.search(member, scope);
    }

    final ArrayList<UsageInfo> infos = new ArrayList<UsageInfo>();
    query.forEach(new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference reference) {
        infos.add(new UsageInfo(reference));
        return true;
      }
    });
    return infos;
  }

  public static LinkedHashSet<String> getSuggestedNames(PsiElement psiElement, final PsiElement nameSuggestionContext) {
    final LinkedHashSet<String> result = new LinkedHashSet<String>();
    result.add(UsageViewUtil.getShortName(psiElement));
    final NameSuggestionProvider[] providers = Extensions.getExtensions(NameSuggestionProvider.EP_NAME);
    for (NameSuggestionProvider provider : providers) {
      SuggestedNameInfo info = provider.getSuggestedNames(psiElement, nameSuggestionContext, result);
      if (info != null) {
        if (provider instanceof PreferrableNameSuggestionProvider && !((PreferrableNameSuggestionProvider)provider).shouldCheckOthers()) {
          break;
        }
      }
    }
    return result;
  }


  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrReferenceExpression)) return false;

        GroovyResolveResult result = ((GrReferenceExpression)element).advancedResolve();

        GroovyPsiElement context = result.getCurrentFileResolveContext();
        if (!(context instanceof GrImportStatement)) return false;

        GrImportStatement importStatement = (GrImportStatement)context;
        if (!importStatement.isStatic() || importStatement.isAliasedImport()) return false;

        return true;
      }
    };
  }
}
