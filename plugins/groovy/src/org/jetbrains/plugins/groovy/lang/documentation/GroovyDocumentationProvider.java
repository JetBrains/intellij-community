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
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.editorActions.CodeDocumentationUtil;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;
import java.util.Map;

/**
 * @author ven
 */
public class GroovyDocumentationProvider implements CodeDocumentationProvider, ExternalDocumentationProvider {
  private static final String LINE_SEPARATOR = "\n";

  @NonNls private static final String PARAM_TAG = "@param";
  @NonNls private static final String RETURN_TAG = "@return";
  @NonNls private static final String THROWS_TAG = "@throws";

  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof GrVariable) {
      GrVariable variable = (GrVariable)element;
      StringBuffer buffer = new StringBuffer();
      if (element instanceof GrField) {
        final PsiClass parentClass = ((GrField)element).getContainingClass();
        if (parentClass != null) {
          buffer.append(JavaDocUtil.getShortestClassName(parentClass, element));
          newLine(buffer);
        }
        generateModifiers(buffer, element);
      }
      final PsiType type = variable.getDeclaredType();
      appendTypeString(buffer, type);
      buffer.append(" ");
      buffer.append(variable.getName());
      newLine(buffer);

      PsiReference ref;
      while (originalElement != null && ((ref = originalElement.getReference()) == null || ref.resolve() == null)) {
        originalElement = originalElement.getParent();
      }

      appendInferredType(originalElement, buffer);

      return buffer.toString();
    }
    else if (element instanceof GrReferenceExpression) {
      GrReferenceExpression refExpr = (GrReferenceExpression)element;
      StringBuffer buffer = new StringBuffer();
      PsiType type = null;
      if (refExpr.getParent() instanceof GrAssignmentExpression) {
        GrAssignmentExpression assignment = (GrAssignmentExpression)refExpr.getParent();
        if (refExpr.equals(assignment.getLValue())) {
          GrExpression rvalue = assignment.getRValue();
          if (rvalue != null) {
            type = rvalue.getType();
          }
        }
      }
      appendTypeString(buffer, type);
      buffer.append(" ");
      buffer.append(refExpr.getReferenceName());
      return buffer.toString();
    }
    else if (element instanceof PsiMethod) {
      StringBuffer buffer = new StringBuffer();
      PsiMethod method = (PsiMethod)element;
      if (method instanceof GrGdkMethod) {
        buffer.append("[GDK] ");
      }
      else {
        PsiClass hisClass = method.getContainingClass();
        if (hisClass != null) {
          String qName = hisClass.getQualifiedName();
          if (qName != null) {
            buffer.append(qName).append("\n");
          }
        }
      }

      if (!method.isConstructor()) {
        appendTypeString(buffer, PsiUtil.getSmartReturnType(method));
        buffer.append(" ");
      }
      buffer.append(method.getName()).append(" ");
      buffer.append("(");
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        if (i > 0) buffer.append(", ");
        if (parameter instanceof GrParameter) {
          buffer.append(GroovyPresentationUtil.getParameterPresentation((GrParameter)parameter, PsiSubstitutor.EMPTY));
        }
        else {
          PsiType type = parameter.getType();
          appendTypeString(buffer, type);
          buffer.append(" ");
          buffer.append(parameter.getName());
        }
      }
      buffer.append(")");
      return buffer.toString();
    }
    else if (element instanceof GrTypeDefinition) {
      return generateClassInfo((GrTypeDefinition)element);
    }

    //todo
    return null;
  }

  private static void appendInferredType(PsiElement originalElement, StringBuffer buffer) {
    if (originalElement != null) {
      if (originalElement instanceof GrReferenceExpression) {
        final PsiType inferredType = ((GrReferenceExpression)originalElement).getType();
        if (inferredType != null) {
          buffer.append("[inferred type] ").append(inferredType.getCanonicalText());
          return;
        }
      }
    }
    buffer.append("[cannot infer type]");
  }

  private static void generateModifiers(StringBuffer buffer, PsiElement element) {
    String modifiers = PsiFormatUtil.formatModifiers(element, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);

    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
  }

  private static void newLine(StringBuffer buffer) {
    buffer.append(LINE_SEPARATOR);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String generateClassInfo(PsiClass aClass) {
    StringBuffer buffer = new StringBuffer();
    GroovyFile file = (GroovyFile)aClass.getContainingFile();

    String packageName = file.getPackageName();
    if (packageName.length() > 0) {
      buffer.append(packageName).append("\n");
    }

    final String classString =
      aClass.isInterface() ? "interface" : aClass instanceof PsiTypeParameter ? "type parameter" : aClass.isEnum() ? "enum" : "class";
    buffer.append(classString).append(" ");

    buffer.append(aClass.getName());

    if (aClass.hasTypeParameters()) {
      PsiTypeParameter[] typeParameters = aClass.getTypeParameters();

      buffer.append("<");

      for (int i = 0; i < typeParameters.length; i++) {
        if (i > 0) buffer.append(", ");

        PsiTypeParameter tp = typeParameters[i];

        buffer.append(tp.getName());

        PsiClassType[] refs = tp.getExtendsListTypes();

        if (refs.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < refs.length; j++) {
            if (j > 0) buffer.append(" & ");
            appendTypeString(buffer, refs[j]);
          }
        }
      }

      buffer.append(">");
    }

    PsiClassType[] refs = aClass.getExtendsListTypes();
    if (refs.length > 0 || !aClass.isInterface() && !"java.lang.Object".equals(aClass.getQualifiedName())) {
      buffer.append(" extends ");
      if (refs.length == 0) {
        buffer.append("Object");
      }
      else {
        for (int i = 0; i < refs.length; i++) {
          if (i > 0) buffer.append(", ");
          appendTypeString(buffer, refs[i]);
        }
      }
    }

    refs = aClass.getImplementsListTypes();
    if (refs.length > 0) {
      buffer.append("\nimplements ");
      for (int i = 0; i < refs.length; i++) {
        if (i > 0) buffer.append(", ");
        appendTypeString(buffer, refs[i]);

      }
    }

    return buffer.toString();
  }


  private static void appendTypeString(StringBuffer buffer, PsiType type) {
    if (type != null) {
      buffer.append(type.getCanonicalText());
    }
    else {
      buffer.append(GrModifier.DEF);
    }
  }

  @Nullable
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return JavaDocumentationProvider.getExternalJavaDocUrl(element);
  }

  @Nullable
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    if (element instanceof GrReferenceExpression) {
      return getMethodCandidateInfo((GrReferenceExpression)element);
    }

    if (element instanceof GrGdkMethod) {
      element = ((GrGdkMethod)element).getStaticMethod();
    }
    
    final GrDocComment doc = PsiTreeUtil.getParentOfType(originalElement, GrDocComment.class);
    if (doc != null) {
      element = GrDocCommentUtil.findDocOwner(doc);
    }

    return JavaDocumentationProvider.generateExternalJavadoc(element);
  }

  public String fetchExternalDocumentation(final Project project, PsiElement element, final List<String> docUrls) {
    return JavaDocumentationProvider.fetchExternalJavadoc(element, project, docUrls);
  }

  private static String getMethodCandidateInfo(GrReferenceExpression expr) {
    final GroovyResolveResult[] candidates = expr.multiResolve(false);
    final String text = expr.getText();
    if (candidates.length > 0) {
      @NonNls final StringBuffer sb = new StringBuffer();
      for (final GroovyResolveResult candidate : candidates) {
        final PsiElement element = candidate.getElement();
        if (!(element instanceof PsiMethod)) {
          continue;
        }
        final String str = PsiFormatUtil.formatMethod((PsiMethod)element, candidate.getSubstitutor(),
                                                      PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_PARAMETERS,
                                                      PsiFormatUtil.SHOW_TYPE);
        createElementLink(sb, element, str);
      }
      return CodeInsightBundle.message("javadoc.candiates", text, sb);
    }
    return CodeInsightBundle.message("javadoc.candidates.not.found", text);
  }

  private static void createElementLink(@NonNls final StringBuffer sb, final PsiElement element, final String str) {
    sb.append("&nbsp;&nbsp;<a href=\"psi_element://");
    sb.append(JavaDocUtil.getReferenceText(element.getProject(), element));
    sb.append("\">");
    sb.append(str);
    sb.append("</a>");
    sb.append("<br>");
  }

  @Nullable
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  @Nullable
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  public PsiComment findExistingDocComment(PsiComment contextElement) {
    if (contextElement instanceof GrDocComment) {
      final GrDocCommentOwner owner = GrDocCommentUtil.findDocOwner((GrDocComment)contextElement);
      if (owner != null) {
        return owner.getDocComment();
      }
    }
    return null;
  }

  public String generateDocumentationContentStub(PsiComment contextComment) {
    if (!(contextComment instanceof GrDocComment)) {
      return null;
    }

    final GrDocCommentOwner owner = GrDocCommentUtil.findDocOwner((GrDocComment)contextComment);
    if (owner == null) return null;

    Project project = contextComment.getProject();
    final CodeDocumentationAwareCommenter commenter =
      (CodeDocumentationAwareCommenter)LanguageCommenters.INSTANCE.forLanguage(owner.getLanguage());


    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      if (owner instanceof GrMethod) {
        final GrMethod method = (GrMethod)owner;
        final GrParameter[] parameters = method.getParameters();
        final Map<String, String> param2Description = new HashMap<String, String>();
        final PsiMethod[] superMethods = method.findSuperMethods();

        for (PsiMethod superMethod : superMethods) {
          final PsiDocComment comment = superMethod.getDocComment();
          if (comment != null) {
            final PsiDocTag[] params = comment.findTagsByName("param");
            for (PsiDocTag param : params) {
              final PsiElement[] dataElements = param.getDataElements();
              if (dataElements != null) {
                String paramName = null;
                for (PsiElement dataElement : dataElements) {
                  if (dataElement instanceof PsiDocParamRef) {
                    paramName = dataElement.getReference().getCanonicalText();
                    break;
                  }
                }
                if (paramName != null) {
                  param2Description.put(paramName, param.getText());
                }
              }
            }
          }
        }
        for (PsiParameter parameter : parameters) {
          String description = param2Description.get(parameter.getName());
          if (description != null) {
            builder.append(CodeDocumentationUtil.createDocCommentLine("", project, commenter));
            if (description.indexOf('\n') > -1) description = description.substring(0, description.lastIndexOf('\n'));
            builder.append(description);
          }
          else {
            builder.append(CodeDocumentationUtil.createDocCommentLine(PARAM_TAG, project, commenter));
            builder.append(parameter.getName());
          }
          builder.append(LINE_SEPARATOR);
        }

        final PsiType returnType = method.getInferredReturnType();
        if ((returnType != null || method.getModifierList().hasModifierProperty(GrModifier.DEF)) &&
            returnType != PsiType.VOID) {
          builder.append(CodeDocumentationUtil.createDocCommentLine(RETURN_TAG, project, commenter));
          builder.append(LINE_SEPARATOR);
        }

        final PsiClassType[] references = method.getThrowsList().getReferencedTypes();
        for (PsiClassType reference : references) {
          builder.append(CodeDocumentationUtil.createDocCommentLine(THROWS_TAG, project, commenter));
          builder.append(reference.getClassName());
          builder.append(LINE_SEPARATOR);
        }
      }
      else if (owner instanceof GrTypeDefinition) {
        final PsiTypeParameterList typeParameterList = ((PsiClass)owner).getTypeParameterList();
        if (typeParameterList != null) {
          createTypeParamsListComment(builder, project, commenter, typeParameterList);
        }
      }
      return builder.length() > 0 ? builder.toString() : null;
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  private static void createTypeParamsListComment(final StringBuilder buffer,
                                                  final Project project,
                                                  final CodeDocumentationAwareCommenter commenter,
                                                  final PsiTypeParameterList typeParameterList) {
    final PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
    for (PsiTypeParameter typeParameter : typeParameters) {
      buffer.append(CodeDocumentationUtil.createDocCommentLine(PARAM_TAG, project, commenter));
      buffer.append("<").append(typeParameter.getName()).append(">");
      buffer.append(LINE_SEPARATOR);
    }
  }

}
