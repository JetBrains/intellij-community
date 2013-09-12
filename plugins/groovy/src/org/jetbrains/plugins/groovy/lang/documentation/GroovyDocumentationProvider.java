/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.CustomMembersGenerator;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrPropertyForCompletion;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrImplicitVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
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
  private static final String BODY_HTML = "</body></html>";

  private static PsiSubstitutor calcSubstitutor(PsiElement originalElement) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (originalElement instanceof GrReferenceExpression) {
      substitutor = ((GrReferenceExpression)originalElement).advancedResolve().getSubstitutor();
    }
    return substitutor;
  }


  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof GrVariable || element instanceof GrImplicitVariable) {
      StringBuilder buffer = new StringBuilder();
      PsiVariable variable = (PsiVariable)element;

      if (originalElement instanceof GrVariableDeclaration && ((GrVariableDeclaration)originalElement).getVariables().length > 1) {
        for (GrVariable var :((GrVariableDeclaration)originalElement).getVariables()) {
          generateVariableInfo(originalElement, buffer, var);
          buffer.append("\n\n");
        }
      }
      else {
        generateVariableInfo(originalElement, buffer, variable);
      }
      return buffer.toString();
    }
    else if (element instanceof PsiMethod) {
      StringBuilder buffer = new StringBuilder();
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

      PsiSubstitutor substitutor = calcSubstitutor(originalElement);
      if (!method.isConstructor()) {
        final PsiType substituted = substitutor.substitute(PsiUtil.getSmartReturnType(method));
        appendTypeString(buffer, substituted, originalElement);
        buffer.append(" ");
      }
      buffer.append(method.getName()).append(" ");
      buffer.append("(");
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        if (i > 0) buffer.append(", ");
        if (parameter instanceof GrParameter) {
          GroovyPresentationUtil.appendParameterPresentation((GrParameter)parameter, substitutor, TypePresentation.LINK, buffer);
        }
        else {
          PsiType type = parameter.getType();
          appendTypeString(buffer, substitutor.substitute(type), originalElement);
          buffer.append(" ");
          buffer.append(parameter.getName());
        }
      }
      buffer.append(")");
      final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
      if (referencedTypes.length > 0) {
        buffer.append("\nthrows ");
        for (PsiClassType referencedType : referencedTypes) {
          appendTypeString(buffer, referencedType, originalElement);
          buffer.append(", ");
        }
        buffer.delete(buffer.length() - 2, buffer.length());
      }
      return buffer.toString();
    }
    else if (element instanceof GrTypeDefinition) {
      return generateClassInfo((GrTypeDefinition)element);
    }

    return null;
  }

  private static void generateVariableInfo(PsiElement originalElement, StringBuilder buffer, PsiVariable variable) {
    if (variable instanceof PsiField) {
      final PsiClass parentClass = ((PsiField)variable).getContainingClass();
      if (parentClass != null) {
        buffer.append(JavaDocUtil.getShortestClassName(parentClass, variable));
        newLine(buffer);
      }
      generateModifiers(buffer, variable);
    }
    final PsiType type = variable instanceof GrVariable ? ((GrVariable)variable).getDeclaredType() : variable.getType();
    appendTypeString(buffer, calcSubstitutor(originalElement).substitute(type), originalElement);
    buffer.append(" ");
    buffer.append(variable.getName());

    if (variable instanceof GrVariable) {
      newLine(buffer);

      while (originalElement != null) {
        PsiReference ref = originalElement.getReference();
        if (ref != null && ref.resolve() != null) break;

        originalElement = originalElement.getParent();
      }

      if (originalElement != null) {
        appendInferredType(originalElement, (GrVariable)variable, buffer);
      }
    }
  }

  private static void appendInferredType(PsiElement originalElement, GrVariable variable, StringBuilder buffer) {
    PsiType inferredType = null;
    if (TokenSets.WHITE_SPACES_SET.contains(originalElement.getNode().getElementType())) {
      originalElement = PsiTreeUtil.prevLeaf(originalElement);
    }
    if (originalElement != null && originalElement.getNode().getElementType() == GroovyTokenTypes.mIDENT) {
      originalElement = originalElement.getParent();
    }
    if (originalElement instanceof GrReferenceExpression) {
      inferredType = ((GrReferenceExpression)originalElement).getType();
    }
    else if (originalElement instanceof GrVariableDeclaration) {
      inferredType = variable.getTypeGroovy();
    }
    else if (originalElement instanceof GrVariable) {
      inferredType = ((GrVariable)originalElement).getTypeGroovy();
    }

    if (inferredType != null) {
      buffer.append("[inferred type] ");
      appendTypeString(buffer, inferredType, originalElement);
    }
    else {
      buffer.append("[cannot infer type]");
    }
  }

  private static void generateModifiers(StringBuilder buffer, PsiElement element) {
    String modifiers = PsiFormatUtil.formatModifiers(element, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);

    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
  }

  private static void newLine(StringBuilder buffer) {
    buffer.append(LINE_SEPARATOR);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String generateClassInfo(PsiClass aClass) {
    StringBuilder buffer = new StringBuilder();
    GroovyFile file = (GroovyFile)aClass.getContainingFile();

    String packageName = file.getPackageName();
    if (packageName.length() > 0) {
      buffer.append(packageName).append("\n");
    }

    final String classString =
      aClass.isInterface() ? "interface" : aClass instanceof PsiTypeParameter ? "type parameter" : aClass.isEnum() ? "enum" : "class";
    buffer.append(classString).append(" ").append(aClass.getName());

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
            appendTypeString(buffer, refs[j], aClass);
          }
        }
      }

      buffer.append(">");
    }

    PsiClassType[] refs = aClass.getExtendsListTypes();
    if (refs.length > 0 || !aClass.isInterface() && !CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      buffer.append(" extends ");
      if (refs.length == 0) {
        buffer.append("Object");
      }
      else {
        for (int i = 0; i < refs.length; i++) {
          if (i > 0) buffer.append(", ");
          appendTypeString(buffer, refs[i], aClass);
        }
      }
    }

    refs = aClass.getImplementsListTypes();
    if (refs.length > 0) {
      buffer.append("\nimplements ");
      for (int i = 0; i < refs.length; i++) {
        if (i > 0) buffer.append(", ");
        appendTypeString(buffer, refs[i], aClass);

      }
    }

    return buffer.toString();
  }


  public static void appendTypeString(StringBuilder buffer, final PsiType type, PsiElement context) {
    if (type != null) {
      JavaDocInfoGenerator.generateType(buffer, type, context);
    }
    else {
      buffer.append(GrModifier.DEF);
    }
  }

  @Nullable
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    List<String> result = new ArrayList<String>();
    PsiElement docElement = getDocumentationElement(element, originalElement);
    if (docElement != null) {
      ContainerUtil.addIfNotNull(result, docElement.getUserData(NonCodeMembersHolder.DOCUMENTATION_URL));
    }
    List<String> list = JavaDocumentationProvider.getExternalJavaDocUrl(element);
    if (list != null) {
      result.addAll(list);
    }
    return result.isEmpty() ? null : result;
  }

  @Nullable
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    if (element instanceof CustomMembersGenerator.GdslNamedParameter) {
      CustomMembersGenerator.GdslNamedParameter parameter = (CustomMembersGenerator.GdslNamedParameter)element;
      String result = "<pre><b>" + parameter.getName() + "</b>";
      if (parameter.myParameterTypeText != null) {
        result += ": " + parameter.myParameterTypeText;
      }
      result += "</pre>";
      if (parameter.docString != null) {
        result += "<p>" + parameter.docString;
      }
      return result;
    }

    if (element instanceof GrReferenceExpression) {
      return getMethodCandidateInfo((GrReferenceExpression)element);
    }

    element = getDocumentationElement(element, originalElement);

    if (element == null) return null;

    String standard = element.getNavigationElement() instanceof PsiDocCommentOwner ? JavaDocumentationProvider.generateExternalJavadoc(element) : null;

    if (element instanceof GrVariable &&
        ((GrVariable)element).getTypeElementGroovy() == null &&
        standard != null) {
      final String truncated = StringUtil.trimEnd(standard, BODY_HTML);

      StringBuilder buffer = new StringBuilder(truncated);
      buffer.append("<p>");
      if (originalElement != null) {
        appendInferredType(originalElement, (GrVariable)element, buffer);
      }
      else if (element.getParent() instanceof GrVariableDeclaration) {
        appendInferredType(element.getParent(), (GrVariable)element, buffer);
      }

      if (!truncated.equals(standard)) {
        buffer.append(BODY_HTML);
      }
      standard = buffer.toString();
    }

    String gdslDoc = element.getUserData(NonCodeMembersHolder.DOCUMENTATION);
    if (gdslDoc != null) {
      if (standard != null) {
        String truncated = StringUtil.trimEnd(standard, BODY_HTML);
        String appended = truncated + "<p>" + gdslDoc;
        if (truncated.equals(standard)) {
          return appended;
        }
        return appended + BODY_HTML;
      }
      return gdslDoc;
    }

    return standard;
  }

  private static PsiElement getDocumentationElement(PsiElement element, PsiElement originalElement) {
    if (element instanceof GrGdkMethod) {
      element = ((GrGdkMethod)element).getStaticMethod();
    }

    final GrDocComment doc = PsiTreeUtil.getParentOfType(originalElement, GrDocComment.class);
    if (doc != null) {
      element = GrDocCommentUtil.findDocOwner(doc);
    }

    if (element instanceof GrLightVariable) {
      PsiElement navigationElement = element.getNavigationElement();

      if (navigationElement != null) {
        element = navigationElement;

        if (element.getContainingFile() instanceof PsiCompiledFile) {
          navigationElement = element.getNavigationElement();
          if (navigationElement != null) {
            element = navigationElement;
          }
        }

        if (element instanceof GrAccessorMethod) {
          element = ((GrAccessorMethod)element).getProperty();
        }
      }
    }

    if (element instanceof GrPropertyForCompletion) {
      element = ((GrPropertyForCompletion)element).getOriginalAccessor();
    }
    return element;
  }

  public String fetchExternalDocumentation(final Project project, PsiElement element, final List<String> docUrls) {
    return JavaDocumentationProvider.fetchExternalJavadoc(element, project, docUrls);
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement);
  }

  @Override
  public boolean canPromptToConfigureDocumentation(PsiElement element) {
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(PsiElement element) {
  }

  private static String getMethodCandidateInfo(GrReferenceExpression expr) {
    final GroovyResolveResult[] candidates = expr.multiResolve(false);
    final String text = expr.getText();
    if (candidates.length > 0) {
      @NonNls final StringBuilder sb = new StringBuilder();
      for (final GroovyResolveResult candidate : candidates) {
        final PsiElement element = candidate.getElement();
        if (!(element instanceof PsiMethod)) {
          continue;
        }
        final String str = PsiFormatUtil
          .formatMethod((PsiMethod)element, candidate.getSubstitutor(),
                        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_PARAMETERS,
                        PsiFormatUtilBase.SHOW_TYPE);
        createElementLink(sb, element, str);
      }
      return CodeInsightBundle.message("javadoc.candiates", text, sb);
    }
    return CodeInsightBundle.message("javadoc.candidates.not.found", text);
  }

  private static void createElementLink(@NonNls final StringBuilder sb, final PsiElement element, final String str) {
    sb.append("&nbsp;&nbsp;<a href=\"psi_element://");
    sb.append(JavaDocUtil.getReferenceText(element.getProject(), element));
    sb.append("\">");
    sb.append(str);
    sb.append("</a>");
    sb.append("<br>");
  }

  @Nullable
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    if (object instanceof GroovyResolveResult) {
      return ((GroovyResolveResult)object).getElement();
    }
    if (object instanceof NamedArgumentDescriptor) {
      return ((NamedArgumentDescriptor)object).getNavigationElement();
    }
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

  @Nullable
  @Override
  public Pair<PsiElement, PsiComment> parseContext(@NotNull PsiElement startPoint) {
    for (PsiElement e = startPoint; e != null; e = e.getParent()) {
      if (e instanceof GrDocCommentOwner) {
        return Pair.<PsiElement, PsiComment>create(e, ((GrDocCommentOwner)e).getDocComment());
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
