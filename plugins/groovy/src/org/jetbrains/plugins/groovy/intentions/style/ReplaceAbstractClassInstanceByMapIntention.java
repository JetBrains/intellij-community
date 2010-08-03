/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class ReplaceAbstractClassInstanceByMapIntention extends Intention {
  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement psiElement, Project project, Editor editor) throws IncorrectOperationException {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final GrNewExpression newExpr = (GrNewExpression)psiElement;
    GrCodeReferenceElement ref = newExpr.getReferenceElement();
    assert ref != null;

    final PsiElement resolved = ref.resolve();
    assert resolved instanceof PsiClass;// && ((PsiClass)resolved).isInterface();

    final GrAnonymousClassDefinition anonymous = newExpr.getAnonymousClassDefinition();
    assert anonymous != null;
    GrTypeDefinitionBody body = anonymous.getBody();

    HashMap<PsiMethod, List<PsiElement>> myMethodToBodyMap = new HashMap<PsiMethod, List<PsiElement>>();

    if (body != null) {
      for (PsiElement element : body.getChildren()) {
        if (element instanceof GrMethod) {
          GrOpenBlock block = ((GrMethod)element).getBlock();
          if (block != null) {
            ArrayList<PsiElement> list = new ArrayList<PsiElement>();
            for (PsiElement child : block.getChildren()) {
              if (child != block.getLBrace() && child != block.getRBrace()) {
                list.add(child);
              }
            }
            myMethodToBodyMap.put(((GrMethod)element), list);
          }
        }
      }
    }

    final PsiClass iface = (PsiClass)resolved;
    final Collection<CandidateInfo> collection = GroovyOverrideImplementUtil.getMethodsToImplement(anonymous);
    for (CandidateInfo info : collection) {
      myMethodToBodyMap.put((PsiMethod)info.getElement(), Collections.<PsiElement>emptyList());
    }
    if (myMethodToBodyMap.size() == 1) {
      createSingleMethodWrapper(project, iface, newExpr, myMethodToBodyMap);
    }
    else {
      createMultipleMethodWrapper(project, iface, newExpr, myMethodToBodyMap);
    }
  }

  private static void createMultipleMethodWrapper(final Project project,
                                                  final PsiClass iface,
                                                  final GrNewExpression newExpression,
                                                  final HashMap<PsiMethod, List<PsiElement>> methodToBodyMap)
    throws IncorrectOperationException {

    final ArrayList<PsiType> typesToImport = new ArrayList<PsiType>();
    StringBuffer buffer = new StringBuffer();
    final int length = methodToBodyMap.size();
    buffer.append("[");
    final Iterator<PsiMethod> iterator = methodToBodyMap.keySet().iterator();
    if (iterator.hasNext()) {
      buffer.append("\n");
      appendMethodEntry(iterator.next(), typesToImport, buffer, methodToBodyMap);
    }
    while (iterator.hasNext()) {
      buffer.append(",\n");
      appendMethodEntry(iterator.next(), typesToImport, buffer, methodToBodyMap);
    }
    if (length > 0) {
      buffer.append("\n");
    }
    buffer.append("]");

    buffer.append(" as ").append(iface.getName());

    createAndAdjustNewExpression(project, newExpression, typesToImport, buffer);
  }

  private static void appendMethodEntry(final PsiMethod method,
                                        final ArrayList<PsiType> typesToImport,
                                        final StringBuffer buffer,
                                        HashMap<PsiMethod, List<PsiElement>> methodToBodyMap) {
    buffer.append(method.getName()).append(":").append(" ");
    appendClosureTextByMethod(method, buffer, typesToImport, methodToBodyMap);
  }

  private static void createSingleMethodWrapper(final Project project,
                                                final PsiClass iface,
                                                final GrNewExpression newExpression,
                                                HashMap<PsiMethod, List<PsiElement>> methodToBodyMap) throws IncorrectOperationException {
    final ArrayList<PsiType> typesToImport = new ArrayList<PsiType>();
    StringBuffer buffer = new StringBuffer();

    final PsiMethod method = methodToBodyMap.keySet().iterator().next();
    // Create closure text
    appendClosureTextByMethod(method, buffer, typesToImport, methodToBodyMap);
    // create safe type cast
    buffer.append(" as ").append(iface.getName());
    createAndAdjustNewExpression(project, newExpression, typesToImport, buffer);
  }

  private static void createAndAdjustNewExpression(final Project project,
                                                   final GrNewExpression newExpression,
                                                   final ArrayList<PsiType> typesToImport,
                                                   final StringBuffer buffer) throws IncorrectOperationException {
    final PsiFile file = newExpression.getContainingFile();

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    final GrExpression expr = factory.createExpressionFromText(buffer.toString());
    final GrExpression safeTypeExpr = newExpression.replaceWithExpression(expr, false);

    //add necessary imports
    if (file instanceof GroovyFileBase) {
      final GroovyFileBase fileBase = (GroovyFileBase)file;
      //collect unresolved references
      final List<GrCodeReferenceElement> unresolved = new ArrayList<GrCodeReferenceElement>();
      safeTypeExpr.accept(new GroovyElementVisitor() {
        public void visitElement(final GroovyPsiElement element) {
          for (PsiElement psiElement : element.getChildren()) {
            if (psiElement instanceof GroovyPsiElement) {
              ((GroovyPsiElement)psiElement).accept(this);
            }
          }
        }

        @Override
        public void visitCodeReferenceElement(final GrCodeReferenceElement refElement) {
          final String name = refElement.getReferenceName();
          if (refElement.getQualifier() == null && refElement.resolve() == null && name != null) {
            unresolved.add(refElement);
          }
        }
      });

      for (PsiType type : typesToImport) {
        if (type instanceof PsiClassType) {
          final PsiClass clazz = ((PsiClassType)type).resolve();
          for (GrCodeReferenceElement element : unresolved) {
            if (clazz != null && clazz.getName() != null && clazz.getName().equals(element.getReferenceName())) {
              fileBase.addImportForClass(clazz);
            }
          }
        }
      }
    }

    //place caret to correct place
//    moveCaretToCorrectPosition(editor, safeTypeExpr);
  }

  private static void appendClosureTextByMethod(final PsiMethod method,
                                                final StringBuffer buffer,
                                                final ArrayList<PsiType> typesToImport,
                                                HashMap<PsiMethod, List<PsiElement>> methodToBodyMap) {
    final PsiParameterList list = method.getParameterList();
    buffer.append("{ ");
    final PsiParameter[] parameters = list.getParameters();
    Set<String> generatedNames = new HashSet<String>();
    if (parameters.length > 0) {
      final PsiParameter first = parameters[0];
      final PsiType type = first.getType();
      typesToImport.add(type);
      buffer.append(type.getPresentableText()).append(" ");
      buffer.append(createName(generatedNames, first, type));
    }
    for (int i = 1; i < parameters.length; i++) {
      buffer.append(", ");
      final PsiParameter param = parameters[i];
      final PsiType type = param.getType();
      typesToImport.add(type);
      buffer.append(type.getPresentableText()).append(" ");
      String name = createName(generatedNames, param, type);
      buffer.append(name);
    }
    if (parameters.length > 0) {
      buffer.append(" ->\n");
    }

    for (PsiElement element : methodToBodyMap.get(method)) {
      buffer.append(element.getText()).append("\n");
    }

    buffer.append(" }");
  }

  private static String createName(final Set<String> generatedNames, final PsiParameter param, final PsiType type) {
    String name = param.getName();
    if (name == null) {
      name = generateNameByType(type, generatedNames);
      assert name != null;
    }
    generatedNames.add(name);
    return name;
  }

  private static String generateNameByType(final PsiType type, final Set<String> set) {
    final String text = type.getPresentableText();
    final ArrayList<String> strings = GroovyNamesUtil.camelizeString(text);
    assert strings.size() > 0;
    final String last = strings.get(strings.size() - 1).toLowerCase();
    int i = 1;
    String name = last;
    while (set.contains(name)) {
      name = last + i;
      i++;
    }
    return name;
  }

  static class MyPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      if (element instanceof GrNewExpression) {
        GrNewExpression newExpression = (GrNewExpression)element;
        final GrAnonymousClassDefinition anonymous = newExpression.getAnonymousClassDefinition();
        if (newExpression.getQualifier() == null && anonymous != null && anonymous.getFields().length == 0) {
          return true;
        }
      }
      return false;
    }
  }
}


