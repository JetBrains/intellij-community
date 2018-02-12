// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.aliasImport;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GrImportAlias;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.*;

/**
 * @author Max Medvedev
 */
public class GrAliasImportIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final GrImportStatement context;
    final PsiMember resolved;
    if (element instanceof GrReferenceExpression) {
      GrReferenceExpression ref = (GrReferenceExpression)element;
      GroovyResolveResult result = ref.advancedResolve();
      context = (GrImportStatement)result.getCurrentFileResolveContext();
      assert context != null;
      resolved = (PsiMember)result.getElement();
    }
    else if (element instanceof GrImportStatement) {
      context = (GrImportStatement)element;
      GrCodeReferenceElement reference = context.getImportReference();
      assert reference != null;
      resolved = (PsiMember)reference.resolve();
    }
    else {
      return;
    }

    assert resolved != null;
    doRefactoring(project, context, resolved);
  }

  private static void doRefactoring(@NotNull Project project, @NotNull GrImportStatement importStatement, @NotNull PsiMember member) {
    if (member instanceof GrAccessorMethod &&
        !importStatement.isOnDemand() &&
        !Objects.equals(importStatement.getImportedName(), member.getName())) {
      member = ((GrAccessorMethod)member).getProperty();
    }

    final GroovyFileBase file = (GroovyFileBase)importStatement.getContainingFile();
    final List<UsageInfo> usages = findUsages(member, file);
    GrImportStatement templateImport = createTemplateImport(project, member, file);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!importStatement.isOnDemand()) {
        importStatement.delete();
      }
      updateRefs(usages, member.getName(), templateImport);
    }
    else {
      runTemplate(project, importStatement, member, file, usages, templateImport);
    }
  }

  private static GrImportStatement createTemplateImport(Project project,
                                                        PsiMember resolved,
                                                        GroovyFileBase file) {
    final PsiClass aClass = resolved.getContainingClass();
    assert aClass != null;
    String qname = aClass.getQualifiedName();
    final String name = resolved.getName();

    GrImportStatement template = GroovyPsiElementFactory.getInstance(project)
      .createImportStatementFromText("import static " + qname + "." + name + " as aliased");
    return file.addImport(template);
  }

  private static void runTemplate(Project project,
                                  final GrImportStatement context,
                                  PsiMember resolved,
                                  final GroovyFileBase file,
                                  final List<UsageInfo> usages,
                                  GrImportStatement templateImport) {
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();

    TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(templateImport);

    LinkedHashSet<String> names = getSuggestedNames(resolved, context);

    final GrImportAlias alias = templateImport.getAlias();
    assert alias != null;
    final PsiElement aliasNameElement = alias.getNameElement();
    assert aliasNameElement != null;
    templateBuilder.replaceElement(aliasNameElement, new MyLookupExpression(resolved.getName(), names, (PsiNamedElement)resolved, resolved, true, null));
    Template built = templateBuilder.buildTemplate();

    final Editor newEditor = IntentionUtils.positionCursor(project, file, templateImport);
    final Document document = newEditor.getDocument();

    final RangeMarker contextImportPointer = document.createRangeMarker(context.getTextRange());

    final TextRange range = templateImport.getTextRange();
    document.deleteString(range.getStartOffset(), range.getEndOffset());

    final String name = resolved.getName();

    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(newEditor, built, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        final GrImportStatement importStatement = ReadAction
          .compute(() -> PsiTreeUtil.findElementOfClassAtOffset(file, range.getStartOffset(), GrImportStatement.class, true));

        if (brokenOff) {
          if (importStatement != null) {
            ApplicationManager.getApplication().runWriteAction(() -> importStatement.delete());
          }
          return;
        }

        updateRefs(usages, name, importStatement);

        ApplicationManager.getApplication().runWriteAction(() -> {
          final GrImportStatement context1 = PsiTreeUtil.findElementOfClassAtRange(file, contextImportPointer.getStartOffset(),
                                                                                   contextImportPointer.getEndOffset(),
                                                                                   GrImportStatement.class);
          if (context1 != null) {
            context1.delete();
          }
        });
      }
    });
  }

  private static void updateRefs(List<UsageInfo> usages, final String memberName, final GrImportStatement updatedImport) {

    if (updatedImport == null) return;

    final String name = ReadAction.compute(() -> updatedImport.getImportedName());

    for (final UsageInfo usage : usages) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        final PsiElement usageElement = usage.getElement();
        if (usageElement == null) return;

        if (usageElement.getParent() instanceof GrImportStatement) return;

        if (usageElement instanceof GrReferenceElement) {
          final GrReferenceElement ref = (GrReferenceElement)usageElement;
          final PsiElement qualifier = ref.getQualifier();

          if (qualifier == null) {
            final String refName = ref.getReferenceName();
            if (refName == null) return;

            if (memberName.equals(refName)) {
              ref.handleElementRename(name);
            }
            else if (refName.equals(GroovyPropertyUtils.getPropertyNameByAccessorName(memberName))) {
              final String newPropName = GroovyPropertyUtils.getPropertyNameByAccessorName(name);
              if (newPropName != null) {
                ref.handleElementRename(newPropName);
              }
              else {
                ref.handleElementRename(name);
              }
            }
            else if (refName.equals(GroovyPropertyUtils.getGetterNameBoolean(memberName))) {
              final String getterName = GroovyPropertyUtils.getGetterNameBoolean(name);
              ref.handleElementRename(getterName);
            }
            else if (refName.equals(GroovyPropertyUtils.getGetterNameNonBoolean(memberName))) {
              final String getterName = GroovyPropertyUtils.getGetterNameNonBoolean(name);
              ref.handleElementRename(getterName);
            }
            else if (refName.equals(GroovyPropertyUtils.getSetterName(memberName))) {
              final String getterName = GroovyPropertyUtils.getSetterName(name);
              ref.handleElementRename(getterName);
            }
          }
        }
      });
    }
  }

  private static List<UsageInfo> findUsages(PsiMember member, GroovyFileBase file) {
    LocalSearchScope scope = new LocalSearchScope(file);

    final ArrayList<UsageInfo> infos = new ArrayList<>();
    final HashSet<Object> usedRefs = ContainerUtil.newHashSet();

    final Processor<PsiReference> consumer = reference -> {
      if (usedRefs.add(reference)) {
        infos.add(new UsageInfo(reference));
      }

      return true;
    };


    if (member instanceof PsiMethod) {
      MethodReferencesSearch.search((PsiMethod)member, scope, false).forEach(consumer);
    }
    else {
      ReferencesSearch.search(member, scope).forEach(consumer);
      if (member instanceof PsiField) {
        final PsiMethod getter = GroovyPropertyUtils.findGetterForField((PsiField)member);
        if (getter != null) {
          MethodReferencesSearch.search(getter, scope, false).forEach(consumer);
        }
        final PsiMethod setter = GroovyPropertyUtils.findSetterForField((PsiField)member);
        if (setter != null) {
          MethodReferencesSearch.search(setter, scope, false).forEach(consumer);
        }
      }
    }

    return infos;
  }

  public static LinkedHashSet<String> getSuggestedNames(PsiElement psiElement, final PsiElement nameSuggestionContext) {
    final LinkedHashSet<String> result = new LinkedHashSet<>();
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
    return AliasImportIntentionPredicate.INSTANCE;
  }
}
