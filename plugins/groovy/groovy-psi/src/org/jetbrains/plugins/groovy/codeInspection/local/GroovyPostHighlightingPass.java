// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.codeInspection.local;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.ext.spock.SpockUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyUnusedImportUtil.unusedImports;

public class GroovyPostHighlightingPass extends TextEditorHighlightingPass {

  private final @NotNull GroovyFile myFile;
  private final @NotNull Editor myEditor;
  private volatile Set<GrImportStatement> myUnusedImports;
  private volatile List<HighlightInfo> myUnusedDeclarations;

  GroovyPostHighlightingPass(@NotNull GroovyFile file, @NotNull Editor editor) {
    super(file.getProject(), editor.getDocument(), true);
    myFile = file;
    myEditor = editor;
  }

  @Override
  public void doCollectInformation(@NotNull final ProgressIndicator progress) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile virtualFile = myFile.getViewProvider().getVirtualFile();
    if (!fileIndex.isInContent(virtualFile)) {
      return;
    }

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    final HighlightDisplayKey unusedDefKey = HighlightDisplayKey.find(GroovyUnusedDeclarationInspection.SHORT_NAME);
    final boolean deadCodeEnabled = profile.isToolEnabled(unusedDefKey, myFile);
    final UnusedDeclarationInspectionBase deadCodeInspection = (UnusedDeclarationInspectionBase)profile.getUnwrappedTool(UnusedDeclarationInspectionBase.SHORT_NAME, myFile);
    final GroovyUsageHelper usageHelper = new GroovyUsageHelper(deadCodeInspection);

    final List<HighlightInfo> unusedDeclarations = new ArrayList<>();

    final Map<GrParameter, Boolean> usedParams = new HashMap<>();
    myFile.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof GrReferenceExpression && !((GrReferenceElement<?>)element).isQualified()) {
          GroovyResolveResult[] results = ((GrReferenceExpression)element).multiResolve(false);
          if (results.length == 0) {
            results = ((GrReferenceExpression)element).multiResolve(true);
          }
          for (GroovyResolveResult result : results) {
            PsiElement resolved = result.getElement();
            if (resolved instanceof GrParameter && resolved.getContainingFile() == myFile) {
              usedParams.put((GrParameter)resolved, Boolean.TRUE);
            }
          }
        }

        if (deadCodeEnabled &&
            element instanceof GrNamedElement && element instanceof PsiModifierListOwner &&
            !UnusedSymbolUtil.isImplicitUsage(element.getProject(), (PsiModifierListOwner)element) &&
            !GroovySuppressableInspectionTool.isElementToolSuppressedIn(element, GroovyUnusedDeclarationInspection.SHORT_NAME)) {
          PsiElement nameId = ((GrNamedElement)element).getNameIdentifierGroovy();
          if (nameId.getNode().getElementType() == GroovyTokenTypes.mIDENT) {
            String name = ((GrNamedElement)element).getName();
            if (element instanceof GrTypeDefinition && !UnusedSymbolUtil.isClassUsed(myProject,
                                                                                     element.getContainingFile(), (GrTypeDefinition)element,
                                                                                     progress, usageHelper
            )) {
              HighlightInfo.Builder builder = UnusedSymbolUtil
                .createUnusedSymbolInfoBuilder(nameId, GroovyBundle.message("text.class.0.is.unused", name), HighlightInfoType.UNUSED_SYMBOL, GroovyUnusedDeclarationInspection.SHORT_NAME);
              IntentionAction action = QuickFixFactory.getInstance().createSafeDeleteFix(element);
              builder.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(unusedDefKey), null, unusedDefKey);
              ContainerUtil.addIfNotNull(unusedDeclarations, builder.create());
            }
            else if (element instanceof GrMethod method) {
              if (SpockUtils.isUnusedInSpock(method, usageHelper)) {
                usageHelper.shouldCheckContributors = false;
              }
              try {
                if (!UnusedSymbolUtil.isMethodReferenced(method.getProject(), method.getContainingFile(), method, progress, usageHelper)) {
                  String message;
                  if (method.isConstructor()) {
                    message = GroovyBundle.message("text.constructor.0.is.unused", name);
                  } else {
                    message = GroovyBundle.message("text.method.0.is.unused", name);
                  }
                  HighlightInfo.Builder builder = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(nameId, message, HighlightInfoType.UNUSED_SYMBOL, GroovyUnusedDeclarationInspection.SHORT_NAME);
                  IntentionAction action = QuickFixFactory.getInstance().createSafeDeleteFix(method);
                  builder.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(unusedDefKey), null, unusedDefKey);
                  ContainerUtil.addIfNotNull(unusedDeclarations, builder.create());
                }
              } finally {
                usageHelper.shouldCheckContributors = true;
              }
            }
            else if (element instanceof GrField && isFieldUnused((GrField)element, progress, usageHelper)) {
              HighlightInfo.Builder builder =
                UnusedSymbolUtil.createUnusedSymbolInfoBuilder(nameId, GroovyBundle.message("text.property.0.is.unused", name), HighlightInfoType.UNUSED_SYMBOL, GroovyUnusedDeclarationInspection.SHORT_NAME);
              IntentionAction action = QuickFixFactory.getInstance().createSafeDeleteFix(element);
              builder.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(unusedDefKey), null, unusedDefKey);
              ContainerUtil.addIfNotNull(unusedDeclarations, builder.create());
            }
            else if (element instanceof GrParameter) {
              if (!usedParams.containsKey(element)) {
                usedParams.put((GrParameter)element, Boolean.FALSE);
              }
            }
          }
        }

        super.visitElement(element);
      }
    });
    myUnusedImports = unusedImports(myFile);

    if (deadCodeEnabled) {
      for (GrParameter parameter : usedParams.keySet()) {
        if (usedParams.get(parameter)) continue;

        PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof GrMethod method) {
          if (methodMayHaveUnusedParameters(method)) {
            PsiElement identifier = parameter.getNameIdentifierGroovy();
            HighlightInfo.Builder builder = UnusedSymbolUtil
              .createUnusedSymbolInfoBuilder(identifier, GroovyBundle.message("text.parameter.0.is.unused", parameter.getName()), HighlightInfoType.UNUSED_SYMBOL, GroovyUnusedDeclarationInspection.SHORT_NAME);
            IntentionAction action = GroovyQuickFixFactory.getInstance().createRemoveUnusedGrParameterFix(parameter);
            builder.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(unusedDefKey), null, unusedDefKey);
            ContainerUtil.addIfNotNull(unusedDeclarations, builder.create());
          }
        }
        else if (scope instanceof GrClosableBlock) {
          //todo Max Medvedev
        }
      }
    }
    myUnusedDeclarations = unusedDeclarations;
  }

  private static boolean methodMayHaveUnusedParameters(GrMethod method) {
    return (method.isConstructor() ||
         method.hasModifierProperty(PsiModifier.PRIVATE) ||
         method.hasModifierProperty(PsiModifier.STATIC) ||
         !method.hasModifierProperty(PsiModifier.ABSTRACT) && !isOverriddenOrOverrides(method)) &&
        !method.hasModifierProperty(PsiModifier.NATIVE) &&
        !JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass()) &&
        !PsiClassImplUtil.isMainOrPremainMethod(method);
  }

  private static boolean isFieldUnused(GrField field, ProgressIndicator progress, GlobalUsageHelper usageHelper) {
    if (!UnusedSymbolUtil.isFieldUnused(field.getProject(), field.getContainingFile(), field, progress, usageHelper)) return false;
    final GrAccessorMethod[] getters = field.getGetters();
    final GrAccessorMethod setter = field.getSetter();

    for (GrAccessorMethod getter : getters) {
      if (getter.findSuperMethods().length > 0) {
        return false;
      }
    }

    if (setter != null) {
      if (setter.findSuperMethods().length > 0) {
        return false;
      }
    }

    return !UnusedSymbolUtil.isImplicitRead(field) && !UnusedSymbolUtil.isImplicitWrite(field);
  }

  private static boolean isOverriddenOrOverrides(PsiMethod method) {
    boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
    return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
  }

  @Override
  public void doApplyInformationToEditor() {
    if (myUnusedDeclarations == null || myUnusedImports == null) {
      return;
    }

    List<HighlightInfo> infos = new ArrayList<>(myUnusedDeclarations);
    for (GrImportStatement unusedImport : myUnusedImports) {
      IntentionAction action = GroovyQuickFixFactory.getInstance().createOptimizeImportsFix(false);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.UNUSED_SYMBOL).range(calculateRangeToUse(unusedImport))
        .descriptionAndTooltip(GroovyBundle.message("unused.import"))
        .registerFix(action, List.of(), null, null, null)
        .create();
      if (info != null) {
        infos.add(info);
      }
    }

    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), infos, getColorsScheme(), getId());

    if (myUnusedImports != null && !myUnusedImports.isEmpty()) {
      IntentionAction fix = GroovyQuickFixFactory.getInstance().createOptimizeImportsFix(true);
      if (fix.isAvailable(myProject, myEditor, myFile) && myFile.isWritable()) {
        fix.invoke(myProject, myEditor, myFile);
      }
    }
  }


  private static TextRange calculateRangeToUse(GrImportStatement unusedImport) {
    final TextRange range = unusedImport.getTextRange();

    if (StringUtil.isEmptyOrSpaces(unusedImport.getAnnotationList().getText())) return range;

    int start = 0;
    for (PsiElement child = unusedImport.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNode().getElementType() == GroovyTokenTypes.kIMPORT) {
        start = child.getTextRange().getStartOffset();
      }
    }
    return new TextRange(start, range.getEndOffset());
  }

  private static class GroovyUsageHelper extends GlobalUsageHelper {
    private boolean shouldCheckContributors = true;
    private final UnusedDeclarationInspectionBase deadCodeInspection;

    private GroovyUsageHelper(UnusedDeclarationInspectionBase inspection) { deadCodeInspection = inspection; }

    @Override
    public boolean isCurrentFileAlreadyChecked() {
      return false;
    }

    @Override
    public boolean isLocallyUsed(@NotNull PsiNamedElement member) {
      return false;
    }

    @Override
    public boolean shouldCheckUsages(@NotNull PsiMember member) {
      if (shouldCheckContributors) {
        return deadCodeInspection == null || !deadCodeInspection.isEntryPoint(member);
      } else {
        return true;
      }
    }
  }



}
