// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.editorActions.CodeDocumentationUtil;
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationSettings;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.dsl.GdslNamedParameter;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.completion.GrPropertyForCompletion;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrImplicitVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMapProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.lang.documentation.QuickDocHighlightingHelper.appendStyledSignatureFragment;


public class GroovyDocumentationProvider implements CodeDocumentationProvider, ExternalDocumentationProvider {
  private static final String LINE_SEPARATOR = "\n";

  @NonNls private static final String RETURN_TAG = "@return";
  @NonNls private static final String THROWS_TAG = "@throws";
  private static final String BODY_HTML = "</body></html>";

  private static void appendStyledSpan(
    @NotNull StringBuilder buffer,
    @Nullable String value,
    String @NotNull ... properties
  ) {
    if (DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled()) {
      HtmlSyntaxInfoUtil.appendStyledSpan(buffer, value, properties);
    }
    else {
      buffer.append(value);
    }
  }

  private static PsiSubstitutor calcSubstitutor(PsiElement originalElement) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (originalElement instanceof GrReferenceExpression) {
      substitutor = ((GrReferenceExpression)originalElement).advancedResolve().getSubstitutor();
    }
    return substitutor;
  }

  @Override
  @Nullable
  public @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof GrVariable || element instanceof GrImplicitVariable) {
      @Nls StringBuilder buffer = new StringBuilder();
      PsiVariable variable = (PsiVariable)element;

      if (originalElement instanceof GrVariableDeclaration && ((GrVariableDeclaration)originalElement).getVariables().length > 1) {
        for (GrVariable var : ((GrVariableDeclaration)originalElement).getVariables()) {
          generateVariableInfo(originalElement, buffer, var);
          buffer.append("\n\n");
        }
      }
      else {
        generateVariableInfo(originalElement, buffer, variable);
      }
      return buffer.toString();
    }
    else if (element instanceof PsiMethod method) {
      @Nls StringBuilder buffer = new StringBuilder();
      if (method instanceof GrGdkMethod) {
        appendStyledSpan(buffer, "[" + GroovyBundle.message("documentation.gdk.label") + "]", "color: #909090");
      }
      else {
        PsiClass hisClass = method.getContainingClass();
        if (hisClass != null) {
          String qName = hisClass.getQualifiedName();
          if (qName != null) {
            appendStyledSignatureFragment(buffer, qName, GroovyDocHighlightingManager.getInstance().getClassDeclarationAttributes(hisClass));
            buffer.append("\n");
          }
        }
      }

      PsiSubstitutor substitutor = calcSubstitutor(originalElement);
      if (!method.isConstructor()) {
        final PsiType substituted = substitutor.substitute(PsiUtil.getSmartReturnType(method));
        appendTypeString(buffer, substituted, originalElement, false);
        buffer.append(" ");
      }
      appendStyledSignatureFragment(buffer, method.getName(),
                                    GroovyDocHighlightingManager.getInstance().getMethodDeclarationAttributes(method));
      appendStyledSignatureFragment(buffer, "(", GroovyDocHighlightingManager.getInstance().getParenthesesAttributes());
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        if (i > 0) appendStyledSignatureFragment(buffer, ", ", GroovyDocHighlightingManager.getInstance().getCommaAttributes());
        if (parameter instanceof GrParameter) {
          GroovyPresentationUtil.appendParameterPresentation(
            (GrParameter)parameter, substitutor, TypePresentation.LINK, buffer,
            DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled());
        }
        else {
          PsiType type = parameter.getType();
          appendTypeString(buffer, substitutor.substitute(type), originalElement, false);
          buffer.append(" ");
          appendStyledSignatureFragment(buffer, parameter.getName(), GroovyDocHighlightingManager.getInstance().getParameterAttributes());
        }
      }
      appendStyledSignatureFragment(buffer, ")", GroovyDocHighlightingManager.getInstance().getParenthesesAttributes());
      final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
      if (referencedTypes.length > 0) {
        appendStyledSignatureFragment(buffer, "\nthrows ", GroovyDocHighlightingManager.getInstance().getKeywordAttributes());
        for (int i = 0; i < referencedTypes.length; i++) {
          PsiClassType referencedType = referencedTypes[i];
          appendTypeString(buffer, referencedType, originalElement, false);
          if (i != referencedTypes.length - 1) {
            appendStyledSignatureFragment(buffer, ", ", GroovyDocHighlightingManager.getInstance().getCommaAttributes());
          }
        }
      }
      return buffer.toString();
    }
    else if (element instanceof GrTypeDefinition) {
      return generateClassInfo((GrTypeDefinition)element);
    }
    else if (element instanceof GroovyMapProperty mapProperty) {
      @Nls StringBuilder buffer = new StringBuilder();
      appendStyledSpan(buffer, "Map property\n");
      PsiType inferredType = mapProperty.getPropertyType();
      String typeLabel = inferredType != null
                         ? GroovyBundle.message("documentation.inferred.type.label")
                         : GroovyBundle.message("documentation.cannot.infer.type.label");
      appendStyledSpan(buffer, "[" + typeLabel + "]", "color: #909090");
      if (inferredType != null) {
        buffer.append(" ");
        appendTypeString(buffer, inferredType, originalElement, false);
      }
      buffer.append(" ");
      appendStyledSignatureFragment(buffer, mapProperty.getName(), GroovyDocHighlightingManager.getInstance().getClassNameAttributes());
      return buffer.toString();
    }

    return null;
  }

  private static void generateVariableInfo(PsiElement originalElement, @Nls StringBuilder buffer, PsiVariable variable) {
    if (variable instanceof PsiField) {
      final PsiClass parentClass = ((PsiField)variable).getContainingClass();
      if (parentClass != null) {
        appendElementLink(buffer, parentClass, JavaDocUtil.getShortestClassName(parentClass, variable));
        newLine(buffer);
      }
      generateModifiers(buffer, variable);
    }
    final PsiType type = variable instanceof GrVariable ? ((GrVariable)variable).getDeclaredType() : variable.getType();
    appendTypeString(buffer, calcSubstitutor(originalElement).substitute(type), originalElement, false);
    buffer.append(" ");
    TextAttributes varAttributes =
      variable instanceof PsiField
      ? GroovyDocHighlightingManager.getInstance().getFieldDeclarationAttributes((PsiField)variable)
      : GroovyDocHighlightingManager.getInstance().getLocalVariableAttributes();
    appendStyledSignatureFragment(buffer, variable.getName(), varAttributes);

    if (variable instanceof GrVariable) {
      newLine(buffer);

      while (originalElement != null) {
        PsiReference ref = originalElement.getReference();
        if (ref != null && ref.resolve() != null) break;

        originalElement = originalElement.getParent();
      }

      if (originalElement != null) {
        appendInferredType(originalElement, (GrVariable)variable, buffer, false);
      }
    }
  }

  private static void appendInferredType(PsiElement originalElement, GrVariable variable, @Nls StringBuilder buffer, boolean isRendered) {
    PsiType inferredType = null;
    if (PsiImplUtil.isWhiteSpaceOrNls(originalElement)) {
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

    String typeLabel = inferredType != null
                       ? GroovyBundle.message("documentation.inferred.type.label")
                       : GroovyBundle.message("documentation.cannot.infer.type.label");
    appendStyledSpan(buffer, "[" + typeLabel + "]", "color: #909090");
    if (inferredType != null) {
      buffer.append(" ");
      appendTypeString(buffer, inferredType, originalElement, isRendered);
    }
  }

  private static void generateModifiers(@Nls StringBuilder buffer, PsiModifierListOwner element) {
    String modifiers = PsiFormatUtil.formatModifiers(element, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (!modifiers.isEmpty()) {
      appendStyledSignatureFragment(buffer, modifiers, GroovyDocHighlightingManager.getInstance().getKeywordAttributes());
      buffer.append(" ");
    }
  }

  private static void newLine(StringBuilder buffer) {
    buffer.append(LINE_SEPARATOR);
  }

  private static @Nls @NotNull String generateClassInfo(@NotNull PsiClass aClass) {
    @Nls StringBuilder buffer = new StringBuilder();
    GroovyFile file = (GroovyFile)aClass.getContainingFile();

    GroovyDocHighlightingManager highlightingManager = GroovyDocHighlightingManager.getInstance();

    String packageName = file.getPackageName();
    if (!packageName.isEmpty()) {
      appendStyledSignatureFragment(buffer, packageName, highlightingManager.getClassNameAttributes());
      buffer.append("\n");
    }

    final String classString = aClass.isInterface()
                               ? GroovyBundle.message("groovy.term.interface")
                               : aClass instanceof PsiTypeParameter
                                 ? GroovyBundle.message("groovy.term.type.parameter")
                                 : aClass.isEnum()
                                   ? GroovyBundle.message("groovy.term.enum")
                                   : GroovyBundle.message("groovy.term.class");
    appendStyledSignatureFragment(buffer, classString, highlightingManager.getKeywordAttributes());
    buffer.append(" ");
    appendStyledSignatureFragment(buffer, aClass.getName(), highlightingManager.getClassDeclarationAttributes(aClass));

    JavaDocumentationProvider.generateTypeParameters(aClass, buffer, highlightingManager);

    JavaDocumentationProvider.writeExtends(aClass, buffer, aClass.getExtendsListTypes(), highlightingManager);
    JavaDocumentationProvider.writeImplements(aClass, buffer, aClass.getImplementsListTypes(), highlightingManager);

    return buffer.toString();
  }

  public static void appendTypeString(@Nls @NotNull StringBuilder buffer, @Nullable PsiType type, PsiElement context, boolean isRendered) {
    if (type instanceof GrTraitType) {
      generateTraitType(buffer, ((GrTraitType)type), context, isRendered);
    }
    else if (type != null) {
      JavaDocInfoGeneratorFactory.getBuilder(context.getProject())
        .setHighlightingManager(GroovyDocHighlightingManager.getInstance())
        .setIsGenerationForRenderedDoc(isRendered)
        .create()
        .generateType(buffer, type, context);
    }
    else {
      appendStyledSignatureFragment(buffer, GrModifier.DEF, GroovyDocHighlightingManager.getInstance().getKeywordAttributes());
    }
  }

  private static void generateTraitType(@NotNull StringBuilder buffer, @NotNull GrTraitType type, PsiElement context, boolean isRendered) {
    appendTypeString(buffer, type.getExprType(), context, isRendered);
    appendStyledSignatureFragment(buffer, " as ", GroovyDocHighlightingManager.getInstance().getKeywordAttributes()); // <- Groovy keyword
    @NotNull List<PsiType> types = type.getTraitTypes();
    for (int i = 0; i < types.size(); i++) {
      PsiType traitType = types.get(i);
      appendTypeString(buffer, traitType, context, isRendered);
      if (i != types.size() - 1) {
        appendStyledSignatureFragment(buffer, ", ", GroovyDocHighlightingManager.getInstance().getCommaAttributes());
      }
    }
  }

  @Override
  @Nullable
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    List<String> result = new ArrayList<>();
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

  @Override
  @Nullable
  public @Nls String generateDoc(PsiElement element, PsiElement originalElement) {
    GroovyDocHighlightingManager highlightingManager = GroovyDocHighlightingManager.getInstance();

    if (element instanceof GdslNamedParameter) {
      String name = ((GdslNamedParameter)element).getName();
      if (name == null) {
        return null;
      }
      StringBuilder buffer = new StringBuilder("<pre>");
      String parameterTypeText = ((GdslNamedParameter)element).myParameterTypeText;
      appendStyledSignatureFragment(buffer, name, highlightingManager.getParameterAttributes());
      if (parameterTypeText != null) {
        appendStyledSignatureFragment(buffer, ":", highlightingManager.getOperationSignAttributes());
        buffer.append(" ");
        appendStyledSignatureFragment(buffer, parameterTypeText, highlightingManager.getClassNameAttributes());
      }
      buffer.append("</pre>");
      String docString = ((GdslNamedParameter)element).docString;
      if (docString != null) {
        buffer.append("<p>").append(HtmlChunk.text(docString)).append("</p>");
      }
      return buffer.toString(); //NON-NLS
    }

    if (element instanceof GrReferenceExpression) {
      return getMethodCandidateInfo((GrReferenceExpression)element);
    }

    element = getDocumentationElement(element, originalElement);

    if (element == null) return null;

    String standard = generateExternalJavaDoc(element);

    if (element instanceof GrVariable &&
        ((GrVariable)element).getDeclaredType() == null &&
        standard != null) {
      final String truncated = StringUtil.trimEnd(standard, BODY_HTML);

      @Nls StringBuilder buffer = new StringBuilder(truncated);
      buffer.append("<p style='padding-left:8px;'>");
      if (originalElement != null) {
        appendInferredType(originalElement, (GrVariable)element, buffer, false);
      }
      else if (element.getParent() instanceof GrVariableDeclaration) {
        appendInferredType(element.getParent(), (GrVariable)element, buffer, false);
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
        String appended = truncated + "<p>" + gdslDoc; //NON-NLS
        if (truncated.equals(standard)) {
          return appended;
        }
        return appended + BODY_HTML;
      }
      return gdslDoc;
    }

    return standard;
  }

  private static GroovyDocInfoGenerator getDocInfoGenerator(@NotNull PsiElement element, boolean isGenerationForRenderedDoc) {
    return new GroovyDocInfoGenerator(
      element,
      isGenerationForRenderedDoc,
      DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled(),
      DocumentationSettings.isHighlightingOfCodeBlocksEnabled(),
      DocumentationSettings.getInlineCodeHighlightingMode(),
      DocumentationSettings.isSemanticHighlightingOfLinksEnabled(),
      DocumentationSettings.getHighlightingSaturation(isGenerationForRenderedDoc));
  }

  protected static @Nls @Nullable String generateExternalJavaDoc(@NotNull PsiElement element) {
    JavaDocInfoGenerator generator = getDocInfoGenerator(element, false);
    return JavaDocumentationProvider.generateExternalJavadoc(element, generator);
  }

  private static PsiElement getDocumentationElement(PsiElement element, PsiElement originalElement) {
    if (element instanceof GrGdkMethod method) {
      element = method.getStaticMethod();
    }

    final GrDocComment doc = PsiTreeUtil.getParentOfType(originalElement, GrDocComment.class);
    if (doc != null) {
      element = GrDocCommentUtil.findDocOwner(doc);
    }

    if (element instanceof GrLightVariable var) {
      PsiElement navigationElement = var.getNavigationElement();

      element = navigationElement;

      if (element.getContainingFile() instanceof PsiCompiledFile) {
        navigationElement = element.getNavigationElement();
        if (navigationElement != null) {
          element = navigationElement;
        }
      }

      if (element instanceof GrAccessorMethod method) {
        element = method.getProperty();
      }
    }

    if (element instanceof GrPropertyForCompletion property) {
      element = property.getOriginalAccessor();
    }

    return element;
  }

  @Override
  public @Nls String fetchExternalDocumentation(final Project project, PsiElement element, final List<String> docUrls, boolean onHover) {
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

  private static @Nls String getMethodCandidateInfo(GrReferenceExpression expr) {
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
        sb.append("&nbsp;&nbsp;");
        appendElementLink(sb, element, str);
        sb.append("<br>");
      }
      return CodeInsightBundle.message("javadoc.candidates", text, sb);
    }
    return JavaBundle.message("javadoc.candidates.not.found", text);
  }

  private static void appendElementLink(@NonNls StringBuilder sb, PsiElement element, String label) {
    getDocInfoGenerator(element, false)
      .appendMaybeUnresolvedLink(sb, element, label, element.getProject(), false);
  }

  @Override
  @Nullable
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    if (object instanceof GroovyResolveResult) {
      return ((GroovyResolveResult)object).getElement();
    }
    if (object instanceof NamedArgumentDescriptor) {
      return ((NamedArgumentDescriptor)object).getNavigationElement();
    }
    if (object instanceof GrPropertyForCompletion) {
      return ((GrPropertyForCompletion)object).getOriginalAccessor();
    }
    return null;
  }

  @Override
  @Nullable
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  @Override
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
        return Pair.create(e, ((GrDocCommentOwner)e).getDocComment());
      }
    }
    return null;
  }

  @Override
  public String generateDocumentationContentStub(PsiComment contextComment) {
    if (!(contextComment instanceof GrDocComment)) {
      return null;
    }

    final GrDocCommentOwner owner = GrDocCommentUtil.findDocOwner((GrDocComment)contextComment);
    if (owner == null) return null;

    final CodeDocumentationAwareCommenter commenter =
      (CodeDocumentationAwareCommenter)LanguageCommenters.INSTANCE.forLanguage(owner.getLanguage());


    StringBuilder builder = new StringBuilder();
    if (owner instanceof GrMethod method) {
      JavaDocumentationProvider.generateParametersTakingDocFromSuperMethods(builder, commenter, method);

      final PsiType returnType = method.getInferredReturnType();
      if ((returnType != null || method.getModifierList().hasModifierProperty(GrModifier.DEF)) && !PsiTypes.voidType().equals(returnType)) {
        builder.append(CodeDocumentationUtil.createDocCommentLine(RETURN_TAG, contextComment.getContainingFile(), commenter));
        builder.append(LINE_SEPARATOR);
      }

      final PsiClassType[] references = method.getThrowsList().getReferencedTypes();
      for (PsiClassType reference : references) {
        builder.append(CodeDocumentationUtil.createDocCommentLine(THROWS_TAG, contextComment.getContainingFile(), commenter));
        builder.append(reference.getClassName());
        builder.append(LINE_SEPARATOR);
      }
    }
    else if (owner instanceof GrTypeDefinition) {
      final PsiTypeParameterList typeParameterList = ((PsiClass)owner).getTypeParameterList();
      if (typeParameterList != null) {
        JavaDocumentationProvider.createTypeParamsListComment(builder, commenter, typeParameterList);
      }
    }
    return builder.length() > 0 ? builder.toString() : null;
  }

  @Override
  public void collectDocComments(@NotNull PsiFile file,
                                 @NotNull Consumer<? super @NotNull PsiDocCommentBase> sink) {
    if (!(file instanceof GroovyFile groovyFile)) {
      return;
    }
    List<GrDocCommentOwner> owners = PsiTreeUtil.getChildrenOfTypeAsList(groovyFile, GrDocCommentOwner.class);
    List<GrDocComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(groovyFile, GrDocComment.class);
    processDocComments(owners, comments, sink);
  }

  private static void processDocComments(@NotNull List<GrDocCommentOwner> owners,
                                         @NonNls List<GrDocComment> danglingDocComments,
                                         @NotNull Consumer<? super @NotNull PsiDocCommentBase> sink) {
    for (var docComment : danglingDocComments) {
      if (docComment != null) {
        sink.accept(docComment);
      }
    }
    for (var owner : owners) {
      if (owner == null) {
        continue;
      }
      GrDocComment comment = owner.getDocComment();
      if (comment != null) {
        sink.accept(comment);
      }
      if (owner instanceof GrTypeDefinition) {
        var nestedComments = PsiTreeUtil.getChildrenOfTypeAsList(((GrTypeDefinition)owner).getBody(), GrDocComment.class);
        var nestedOwners = PsiTreeUtil.getChildrenOfTypeAsList(((GrTypeDefinition)owner).getBody(), GrDocCommentOwner.class);
        processDocComments(nestedOwners, nestedComments, sink);
      }
    }
  }

  @Override
  public @Nls @Nullable String generateRenderedDoc(@NotNull PsiDocCommentBase comment) {
    PsiElement owner = comment.getOwner();
    String html = getDocInfoGenerator(owner == null ? comment : owner, true).generateRenderedDocInfo();
    return JavaDocExternalFilter.filterInternalDocInfo(html);
  }
}
