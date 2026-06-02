// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class JavaFxComponentIdReferenceProvider extends PsiReferenceProvider {

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                         @NotNull ProcessingContext context) {
    final XmlAttributeValue xmlAttributeValue = (XmlAttributeValue)element;
    final String value = xmlAttributeValue.getValue();
    if (JavaFxPsiUtil.isIncorrectExpressionBinding(value) && !JavaFxPsiUtil.isIncompleteExpressionBinding(value)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final XmlTag currentTag = PsiTreeUtil.getParentOfType(xmlAttributeValue, XmlTag.class);
    final Map<String, XmlAttributeValue> fileIds = JavaFxPsiUtil.collectFileIds(currentTag);

    if (JavaFxPsiUtil.isExpressionBinding(value) || JavaFxPsiUtil.isIncompleteExpressionBinding(value)) {
      String expressionBody = value.endsWith("}") ? value.substring(2, value.length() - 1) : value.substring(2);
      return getExpressionReferences(element, xmlAttributeValue, expressionBody, 2, fileIds);
    }
    if (JavaFxPsiUtil.isChainExpression(value)) {
      return getExpressionReferences(element, xmlAttributeValue, value.substring(1), 1, fileIds);
    }
    final Set<String> acceptableIds = new HashSet<>();
    if (currentTag != null) {
      final XmlTag parentTag = currentTag.getParentTag();
      for (final String id : fileIds.keySet()) {
        final XmlAttributeValue resolvedAttrValue = fileIds.get(id);
        if (JavaFxPsiUtil.isClassAcceptable(parentTag, JavaFxPsiUtil.getTagClass(resolvedAttrValue))) {
          acceptableIds.add(id);
        }
      }
    }
    return new PsiReference[]{new JavaFxIdReferenceBase(xmlAttributeValue, fileIds, acceptableIds, value)};
  }

  private static PsiReference @NotNull [] getExpressionReferences(@NotNull PsiElement element,
                                                                  @NotNull XmlAttributeValue xmlAttributeValue,
                                                                  @NotNull String expressionBody,
                                                                  int offset,
                                                                  @NotNull Map<String, XmlAttributeValue> fileIds) {
    final JavaFxExpressionParser.ParsedBinding parsed = JavaFxExpressionParser.parse(expressionBody);
    if (parsed.chains.isEmpty()) return PsiReference.EMPTY_ARRAY;

    final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(element.getContainingFile());
    final List<PsiReference> result = new ArrayList<>();
    for (JavaFxExpressionParser.PropertyChain chain : parsed.chains) {
      addChainReferences(xmlAttributeValue, fileIds, controllerClass, chain, result, offset);
    }
    return result.toArray(PsiReference.EMPTY_ARRAY);
  }

  private static void addChainReferences(@NotNull XmlAttributeValue xmlAttributeValue,
                                         @NotNull Map<String, XmlAttributeValue> fileIds,
                                         PsiClass controllerClass,
                                         @NotNull JavaFxExpressionParser.PropertyChain chain,
                                         @NotNull List<PsiReference> out,
                                         int offset) {
    final List<JavaFxExpressionParser.Segment> segments = chain.segments;
    final JavaFxExpressionParser.Segment first = segments.getFirst();
    final Map<String, TypeMatch> typeMatches = segments.size() == 1 ? getTypeMatches(xmlAttributeValue, fileIds) : Collections.emptyMap();
    final PsiReferenceBase<?> firstReference =
      getIdReferenceBase(xmlAttributeValue, first.name, fileIds, typeMatches, controllerClass);
    setSegmentRange(firstReference, first, offset);
    out.add(firstReference);

    PsiClass propertyOwnerClass = FxmlConstants.CONTROLLER.equals(first.name)
                                  ? controllerClass
                                  : JavaFxPsiUtil.getTagClass(fileIds.get(first.name));
    for (int i = 1; i < segments.size(); i++) {
      JavaFxExpressionParser.Segment seg = segments.get(i);
      JavaFxExpressionReferenceBase reference =
        new JavaFxExpressionReferenceBase(xmlAttributeValue, propertyOwnerClass, seg.name);
      setSegmentRange(reference, seg, offset);
      PsiType propertyType = JavaFxPsiUtil.getReadablePropertyType(reference.resolve());
      propertyOwnerClass = propertyType instanceof PsiClassType ? ((PsiClassType)propertyType).resolve() : null;
      out.add(reference);
    }
  }

  private static void setSegmentRange(@NotNull PsiReferenceBase<?> reference,
                                      @NotNull JavaFxExpressionParser.Segment segment,
                                      int offset) {
    int valueStart = reference.getRangeInElement().getStartOffset();
    int start = valueStart + offset + segment.offsetInBody;
    reference.setRangeInElement(new TextRange(start, start + segment.name.length()));
  }

  private static Map<String, TypeMatch> getTypeMatches(@NotNull XmlAttributeValue xmlAttributeValue,
                                                       @NotNull Map<String, XmlAttributeValue> fileIds) {
    final PsiClass targetPropertyClass = JavaFxPsiUtil.getWritablePropertyClass(xmlAttributeValue);
    final boolean isConvertible = targetPropertyClass != null && JavaFxPsiUtil.hasConversionFromAnyType(targetPropertyClass);

    return fileIds.entrySet().stream().collect(
      Collectors.toMap(Map.Entry::getKey, e -> {
        final PsiClass valueClass = JavaFxPsiUtil.getTagClassById(e.getValue(), e.getKey(), xmlAttributeValue);
        return TypeMatch.getMatch(valueClass, targetPropertyClass, isConvertible);
      }));
  }

  private static @NotNull PsiReferenceBase getIdReferenceBase(XmlAttributeValue xmlAttributeValue,
                                                              String referencesId,
                                                              Map<String, XmlAttributeValue> fileIds,
                                                              Map<String, TypeMatch> typeMatches,
                                                              PsiClass controllerClass) {
    if (controllerClass != null && !FxmlConstants.CONTROLLER.equals(referencesId)) {
      final PsiField controllerField = controllerClass.findFieldByName(referencesId, false);
      if (controllerField != null) {
        return new JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef(xmlAttributeValue, controllerField, controllerClass);
      }
    }
    return new JavaFxIdReferenceBase(xmlAttributeValue, fileIds, typeMatches, referencesId);
  }

  private enum TypeMatch {
    ASSIGNABLE(3.0),
    CONVERTIBLE(2.0),
    UNDEFINED(1.0),
    INCOMPATIBLE(0.0);

    private final double myPriority;

    TypeMatch(double priority) {
      myPriority = priority;
    }

    public static double getPriority(TypeMatch match) {
      return match != null ? match.myPriority : 0.0;
    }

    public static @NotNull TypeMatch getMatch(PsiClass valueClass, PsiClass targetPropertyClass, boolean isConvertible) {
      if (valueClass == null || targetPropertyClass == null) return UNDEFINED;
      if (InheritanceUtil.isInheritorOrSelf(valueClass, targetPropertyClass, true)) return ASSIGNABLE;
      if (isConvertible) return CONVERTIBLE;
      return INCOMPATIBLE;
    }
  }

  public static final class JavaFxIdReferenceBase extends PsiReferenceBase<XmlAttributeValue> {
    private final Map<String, XmlAttributeValue> myFileIds;
    private final Set<String> myAcceptableIds;
    private final Map<String, TypeMatch> myTypeMatches;
    private final String myReferencesId;

    private JavaFxIdReferenceBase(XmlAttributeValue element,
                                  Map<String, XmlAttributeValue> fileIds,
                                  Set<String> acceptableIds,
                                  String referencesId) {
      super(element);
      myFileIds = fileIds;
      myAcceptableIds = acceptableIds;
      myReferencesId = referencesId;
      myTypeMatches = Collections.emptyMap();
    }

    public JavaFxIdReferenceBase(XmlAttributeValue xmlAttributeValue,
                                 Map<String, XmlAttributeValue> fileIds,
                                 Map<String, TypeMatch> typeMatches,
                                 String referencesId) {
      super(xmlAttributeValue);
      myFileIds = fileIds;
      myTypeMatches = typeMatches;
      myReferencesId = referencesId;
      myAcceptableIds = myFileIds.keySet();
    }

    @Override
    public @Nullable PsiElement resolve() {
      return myFileIds.get(myReferencesId);
    }

    @Override
    public Object @NotNull [] getVariants() {
      boolean incomplete = JavaFxPsiUtil.isIncompleteExpressionBinding(getElement().getValue());
      return myAcceptableIds.stream()
        .map(id -> PrioritizedLookupElement.withPriority(applyInsertHandler(LookupElementBuilder.create(id), incomplete), TypeMatch.getPriority(myTypeMatches.get(id))))
        .toArray(LookupElement[]::new);
    }

    public boolean isBuiltIn() {
      return FxmlConstants.CONTROLLER.equals(myReferencesId) || myReferencesId.endsWith(FxmlConstants.CONTROLLER_SUFFIX);
    }
  }

  private static final class JavaFxExpressionReferenceBase extends JavaFxPropertyReference<XmlAttributeValue> {
    private final String myFieldName;

    JavaFxExpressionReferenceBase(@NotNull XmlAttributeValue xmlAttributeValue, PsiClass tagClass, @NotNull String fieldName) {
      super(xmlAttributeValue, tagClass);
      myFieldName = fieldName;
    }

    @Override
    public @Nullable PsiElement resolve() {
      return JavaFxPsiUtil.getReadableProperties(myPsiClass).get(myFieldName);
    }

    @Override
    public Object @NotNull [] getVariants() {
      final XmlAttributeValue xmlAttributeValue = getElement();
      boolean incomplete = JavaFxPsiUtil.isIncompleteExpressionBinding(xmlAttributeValue.getValue());
      final PsiElement declaration = JavaFxPsiUtil.getAttributeDeclaration(xmlAttributeValue);
      final PsiType propertyType = JavaFxPsiUtil.getWritablePropertyType(myPsiClass, declaration);
      if (propertyType != null) {
        return collectProperties(propertyType, xmlAttributeValue.getProject(), incomplete);
      }
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    private Object[] collectProperties(@NotNull PsiType propertyType, @NotNull Project project, boolean incomplete) {
      final PsiType resolvedType = JavaFxPsiUtil.getWritablePropertyType(propertyType, project);
      final List<LookupElement> objs = new ArrayList<>();
      final Collection<PsiMember> readableProperties = JavaFxPsiUtil.getReadableProperties(myPsiClass).values();
      for (PsiMember readableMember : readableProperties) {
        final PsiType readableType = JavaFxPsiUtil.getReadablePropertyType(readableMember);
        if (readableType == null) continue;
        if (TypeConversionUtil.isAssignable(propertyType, readableType) ||
            resolvedType != null && TypeConversionUtil.isAssignable(resolvedType, readableType)) {
          final String propertyName = PropertyUtilBase.getPropertyName(readableMember);
          if (propertyName != null) {
            objs.add(applyInsertHandler(LookupElementBuilder.create(readableMember, propertyName), incomplete));
          }
        }
      }
      return ArrayUtil.toObjectArray(objs);
    }

    @Override
    public @NotNull String getPropertyName() {
      return myFieldName;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
      final String newPropertyName = JavaFxPsiUtil.getPropertyName(newElementName, resolve() instanceof PsiMethod);
      return super.handleElementRename(newPropertyName);
    }
  }

  private static @NotNull LookupElementBuilder applyInsertHandler(@NotNull LookupElementBuilder builder, boolean incomplete) {
    if (!incomplete) return builder;
    return builder.withInsertHandler(CLOSING_BRACE_INSERT_HANDLER);
  }

  private static final InsertHandler<LookupElement> CLOSING_BRACE_INSERT_HANDLER = (context, ignored) -> {
    Editor editor = context.getEditor();
    int offset = editor.getCaretModel().getOffset();
    CharSequence text = editor.getDocument().getCharsSequence();
    // Only insert } if not already present right after the inserted text
    if (offset >= text.length() || text.charAt(offset) != '}') {
      editor.getDocument().insertString(offset, "}");
    }
  };
}
