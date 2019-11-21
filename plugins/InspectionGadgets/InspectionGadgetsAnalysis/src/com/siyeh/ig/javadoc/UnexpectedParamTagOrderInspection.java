// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inspects the order of the @param tags mentioned in a javadoc comment. Only javadoc comments which belongs to a {@link PsiMethod} or
 * {@link PsiClass} are inspected.
 * <p>
 * The inspection expects at least one parameter or generic type provided by the inspected {@link PsiJavaDocumentedElement} AND at least one
 * documented @param tag. If one of these preconditions isn't met the inspection will not report anything.
 *
 * @see <a href="https://www.oracle.com/technetwork/java/javase/documentation/index-137868.html#multiple@param">How to Write Doc Comments for the Javadoc Tool</a>
 */
public class UnexpectedParamTagOrderInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("inspection.unexpected.param.tag.order.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("inspection.unexpected.param.tag.order.problem");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnexpectedParamTagOrderFix();
  }

  private static class UnexpectedParamTagOrderFix extends InspectionGadgetsFix {

    @Override
    protected void doFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiDocComment docComment = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiDocComment.class);
      if (docComment == null) return;

      final PsiJavaDocumentedElement owner = docComment.getOwner();
      if (!(owner instanceof PsiMethod || owner instanceof PsiClass)) return;

      final PsiDocTag[] paramTags = docComment.findTagsByName("param");
      if (paramTags.length == 0) return;

      final List<String> expectedTagNameOrder = createExpectedTagNameOrder(owner);
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      final List<PsiDocTag> orderedParamTags = createOrderedParamTags(paramTags, expectedTagNameOrder, elementFactory);
      if (orderedParamTags.isEmpty()) return;

      replaceParamTags(docComment, paramTags, orderedParamTags);

      // workaround:
      //
      // Adding new created PsiDocTags (created via elementFactory.createParamTag()) to the docComment (via "docComment.addAfter()") results
      // in a falsely indented @param tag. Such tags have one space more/less as the other @param tags.
      // This can be solved by replacing the docComment with itself.
      docComment.replace(docComment);
    }

    private static void replaceParamTags(@NotNull final PsiDocComment docComment,
                                         @NotNull final PsiDocTag[] currentParamTags,
                                         @NotNull final List<PsiDocTag> newParamTags) {
      // use first param tag as initial anchor, it is guaranteed that currentParamTags isn't empty
      PsiElement anchor = currentParamTags[0];
      for (final PsiDocTag paramTag : newParamTags) {
        anchor = docComment.addAfter(paramTag, anchor);
      }
      // remove duplicates
      for (final PsiDocTag paramTag : currentParamTags) {
        paramTag.delete();
      }
    }

    @NotNull
    private static List<PsiDocTag> createOrderedParamTags(@NotNull final PsiDocTag[] paramTags,
                                                          @NotNull final List<String> expectedTagNameOrder,
                                                          @NotNull final PsiElementFactory elementFactory) {
      final List<PsiDocTag> result = new SmartList<>();
      final Map<String, PsiDocTag> nameToTag = createNameToTag(paramTags);

      int i = -1;
      final int paramTagCount = paramTags.length;
      for (final String expectedTagName : expectedTagNameOrder) {
        i++;
        if (i < paramTagCount) {
          final PsiDocTag paramTag = paramTags[i];
          if (expectedTagName.equals(NameUtil.getName(paramTag))) {
            result.add(paramTag);
            continue;
          }
        }

        final PsiDocTag matchingTag = nameToTag.get(expectedTagName);
        if (matchingTag != null) {
          result.add(matchingTag);
        }
        else {
          result.add(elementFactory.createParamTag(expectedTagName, ""));
        }
      }

      return result;
    }

    @NotNull
    private static Map<String, PsiDocTag> createNameToTag(@NotNull final PsiDocTag[] paramTags) {
      final Map<String, PsiDocTag> result = new HashMap<>();
      for (int i = paramTags.length - 1; i >= 0; i--) {
        final PsiDocTag tag = paramTags[i];
        final String paramTagName = NameUtil.getName(tag);
        if (paramTagName != null) {
          result.put(paramTagName, tag);
        }
      }
      return result;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.unexpected.param.tag.order.quickfix");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnexpectedParamTagOrderVisitor();
  }

  private static class UnexpectedParamTagOrderVisitor extends BaseInspectionVisitor {

    @Override
    public void visitDocComment(@NotNull final PsiDocComment docComment) {
      super.visitDocComment(docComment);
      final PsiJavaDocumentedElement owner = docComment.getOwner();

      if (owner instanceof PsiMethod || owner instanceof PsiClass) {
        checkParamTagOrder(docComment, owner);
      }
    }

    private void checkParamTagOrder(@NotNull final PsiDocComment docComment, @NotNull final PsiJavaDocumentedElement owner) {
      final PsiDocTag[] paramTags = docComment.findTagsByName("param");
      if (paramTags.length == 0) {
        // no parameters documented:
        // therefore nothing to check
        return;
      }

      final List<String> expectedNameOrder = createExpectedTagNameOrder(owner);
      if (expectedNameOrder.isEmpty()) {
        // method without parameters/type-parameters:
        // there could be @param tags defined, but in that case there is no expected order
        // these @param tags should not exist (not handled by this inspection)
        return;
      }

      if (expectedNameOrder.size() != paramTags.length) {
        registerError(docComment.getFirstChild(), owner);
        return;
      }

      for (int i = 0; i < paramTags.length; i++) {
        final String tagName = NameUtil.getName(paramTags[i]);
        final String parameterName = expectedNameOrder.get(i);
        if (!parameterName.equals(tagName)) {
          registerError(docComment.getFirstChild(), owner);
          return;
        }
      }
    }
  }

  @NotNull
  private static List<String> createExpectedTagNameOrder(@NotNull final PsiJavaDocumentedElement owner) {
    final List<String> result = new SmartList<>();

    if (owner instanceof PsiParameterListOwner) {
      final PsiParameter[] parameters = ((PsiParameterListOwner)owner).getParameterList().getParameters();
      for (final PsiParameter p : parameters) {
        result.add(NameUtil.getName(p));
      }
    }

    if (owner instanceof PsiTypeParameterListOwner) {
      final PsiTypeParameter[] parameters = ((PsiTypeParameterListOwner)owner).getTypeParameters();
      for (final PsiTypeParameter p : parameters) {
        result.add(NameUtil.getName(p));
      }
    }

    return result;
  }

  private static class NameUtil {

    @Nullable
    static String getName(@NotNull final PsiDocTag paramTag) {
      final PsiDocTagValue value = paramTag.getValueElement();
      if (value instanceof PsiDocParamRef) {
        final PsiReference reference = value.getReference();
        if (reference != null) {
          final String paramTagName = reference.getCanonicalText();
          if (((PsiDocParamRef)value).isTypeParamRef()) {
            return "<" + paramTagName + ">";
          }
          return paramTagName;
        }
      }

      return null;
    }

    @NotNull
    static String getName(@NotNull final PsiParameter parameter) {
      return parameter.getName();
    }

    @NotNull
    static String getName(@NotNull final PsiTypeParameter parameter) {
      return "<" + parameter.getName() + ">";
    }
  }
}
