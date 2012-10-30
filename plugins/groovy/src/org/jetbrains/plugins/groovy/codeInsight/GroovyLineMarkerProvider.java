/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.JavaLineMarkerProvider;
import com.intellij.codeInsight.daemon.impl.MarkerType;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.FunctionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableDeclarationImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author ilyas
 * Same logic as for Java LMP
 */
public class GroovyLineMarkerProvider extends JavaLineMarkerProvider {

  public GroovyLineMarkerProvider(DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) {
    super(daemonSettings, colorsManager);
  }

  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiNameIdentifierOwner) {
      if (parent instanceof GrField && element == ((GrField)parent).getNameIdentifierGroovy()) {
        for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors((GrField)parent)) {
          MethodSignatureBackedByPsiMethod superSignature = null;
          try {
            superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
          }
          catch (IndexNotReadyException e) {
            //some searchers (EJB) require indices. What shall we do?
          }
          if (superSignature != null) {
            boolean overrides = method.hasModifierProperty(PsiModifier.ABSTRACT) == superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT);
            final Icon icon = overrides ? AllIcons.Gutter.OverridingMethod : AllIcons.Gutter.ImplementingMethod;
            final MarkerType type = GroovyMarkerTypes.OVERRIDING_PROPERTY_TYPE;
            return new LineMarkerInfo<PsiElement>(element, element.getTextRange(), icon, Pass.UPDATE_ALL, type.getTooltip(), type.getNavigationHandler(),
                                                  GutterIconRenderer.Alignment.LEFT);
          }
        }
      }
      else if (parent instanceof GrMethod &&  element == ((GrMethod)parent).getNameIdentifierGroovy() &&
               ((GrMethod)parent).getReflectedMethods().length > 0 &&
               hasSuperMethods((GrMethod)element.getParent())) {
        final Icon icon = AllIcons.Gutter.OverridingMethod;
        final MarkerType type = GroovyMarkerTypes.OVERRIDING_METHOD;
        return new LineMarkerInfo<PsiElement>(element, element.getTextRange(), icon, Pass.UPDATE_ALL, type.getTooltip(),
                                              type.getNavigationHandler(), GutterIconRenderer.Alignment.LEFT);
      }
      

      final ASTNode node = element.getNode();
      if (node != null && TokenSets.PROPERTY_NAMES.contains(node.getElementType())) {
        return super.getLineMarkerInfo(((PsiNameIdentifierOwner)parent).getNameIdentifier());
      }
    }
    //need to draw method separator above docComment
    if (myDaemonSettings.SHOW_METHOD_SEPARATORS && element.getFirstChild() == null) {
      PsiElement element1 = element;
      boolean isMember = false;
      while (element1 != null && !(element1 instanceof PsiFile) && element1.getPrevSibling() == null) {
        element1 = element1.getParent();
        if (element1 instanceof PsiMember || element1 instanceof GrVariableDeclarationImpl) {
          isMember = true;
          break;
        }
      }
      if (isMember && !(element1 instanceof PsiAnonymousClass || element1.getParent() instanceof PsiAnonymousClass)) {
        PsiFile file = element1.getContainingFile();
        Document document = file == null ? null : PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        boolean drawSeparator = false;
        if (document != null) {
          CharSequence documentChars = document.getCharsSequence();

          int category = getGroovyCategory(element1, documentChars);
          for (PsiElement child = element1.getPrevSibling(); child != null; child = child.getPrevSibling()) {
            int category1 = getGroovyCategory(child, documentChars);
            if (category1 == 0) continue;
            drawSeparator = category != 1 || category1 != 1;
            break;
          }
        }

        if (drawSeparator) {
          GrDocComment comment = null;
          if (element1 instanceof GrDocCommentOwner) {
            comment = ((GrDocCommentOwner)element1).getDocComment();
          }
          LineMarkerInfo info =
            new LineMarkerInfo<PsiElement>(element, comment != null ? comment.getTextRange() : element.getTextRange(), null,
                                           Pass.UPDATE_ALL, FunctionUtil.<Object, String>nullConstant(), null,
                                           GutterIconRenderer.Alignment.RIGHT);
          EditorColorsScheme scheme = myColorsManager.getGlobalScheme();
          info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
          info.separatorPlacement = SeparatorPlacement.TOP;
          return info;
        }
      }
    }

    return super.getLineMarkerInfo(element);
  }

  private static boolean hasSuperMethods(GrMethod method) {
    final GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();
    for (GrReflectedMethod reflectedMethod : reflectedMethods) {
      final MethodSignatureBackedByPsiMethod first = SuperMethodsSearch.search(reflectedMethod, null, true, false).findFirst();
      if (first != null) return true;
    }
    return false;
  }

  private static int getGroovyCategory(PsiElement element, CharSequence documentChars) {
    if (element instanceof GrVariableDeclarationImpl) {
      GrVariable[] variables = ((GrVariableDeclarationImpl)element).getVariables();
      if (variables.length == 1 && variables[0] instanceof GrField && variables[0].getInitializerGroovy() instanceof GrClosableBlock) {
        return 2;
      }
    }

    return JavaLineMarkerProvider.getCategory(element, documentChars);
  }

  @Override
  public void collectSlowLineMarkers(@NotNull final List<PsiElement> elements, @NotNull final Collection<LineMarkerInfo> result) {
    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    for (PsiElement element : elements) {
      ProgressManager.checkCanceled();
      if (element instanceof GrField) {
        methods.addAll(GroovyPropertyUtils.getFieldAccessors((GrField)element));
      }
      else if (element instanceof GrMethod) {
        Collections.addAll(methods, ((GrMethod)element).getReflectedMethods());
      }
    }
    collectOverridingMethods(methods, result);

    super.collectSlowLineMarkers(elements, result);
  }

  private static void collectOverridingMethods(final Set<PsiMethod> methods, Collection<LineMarkerInfo> result) {
    final Set<PsiElement> overridden = new HashSet<PsiElement>();

    Set<PsiClass> classes = new THashSet<PsiClass>();
    for (PsiMethod method : methods) {
      ProgressManager.checkCanceled();
      final PsiClass parentClass = method.getContainingClass();
      if (parentClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(parentClass.getQualifiedName())) {
        classes.add(parentClass);
      }
    }

    for (final PsiClass aClass : classes) {
      try {
        AllOverridingMethodsSearch.search(aClass).forEach(new Processor<Pair<PsiMethod, PsiMethod>>() {
          @Override
          public boolean process(final Pair<PsiMethod, PsiMethod> pair) {
            ProgressManager.checkCanceled();

            final PsiMethod superMethod = pair.getFirst();
            if (isCorrectTarget(superMethod) && isCorrectTarget(pair.getSecond())) {
              if (methods.remove(superMethod)) {
                if (superMethod instanceof PsiMirrorElement) {
                  
                }
                overridden.add(PsiImplUtil.handleMirror(superMethod));
              }
            }
            return !methods.isEmpty();
          }
        });
      }
      catch (IndexNotReadyException ignored) {
      }
    }

    for (PsiElement element : overridden) {
      final Icon icon = AllIcons.Gutter.OverridenMethod;

      element = PsiImplUtil.handleMirror(element);

      PsiElement range;
      if (element instanceof GrNamedElement) {
        range = ((GrNamedElement)element).getNameIdentifierGroovy();
      }
      else {
        range = element;
      }

      final MarkerType type;
      if (element instanceof GrField) {
        type = GroovyMarkerTypes.OVERRIDEN_PROPERTY_TYPE;
      }
      else {
        type = GroovyMarkerTypes.OVERRIDEN_METHOD;
      }
      LineMarkerInfo info = new LineMarkerInfo<PsiElement>(range, range.getTextRange(), icon, Pass.UPDATE_OVERRIDEN_MARKERS, type.getTooltip(), type.getNavigationHandler(),
                                                           GutterIconRenderer.Alignment.RIGHT);
      result.add(info);
    }
  }

  private static boolean isCorrectTarget(PsiMethod superMethod) {
    final PsiElement navigationElement = superMethod.getNavigationElement();
    return superMethod.isPhysical() || navigationElement.isPhysical() && !(navigationElement instanceof PsiClass);
  }
}
