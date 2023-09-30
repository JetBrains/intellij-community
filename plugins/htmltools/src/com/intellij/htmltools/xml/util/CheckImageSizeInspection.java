package com.intellij.htmltools.xml.util;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.intellij.images.util.ImageInfoReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 */
public final class CheckImageSizeInspection extends XmlSuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance(CheckImageSizeInspection.class);

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static final class MyFix implements LocalQuickFix {
    private final @IntentionName String myMessage;
    private final @IntentionFamilyName String myFamilyName;
    private final String myAdequateValue;

    private MyFix(@IntentionName String message,
                  @IntentionFamilyName String familyName,
                  String adequateValue) {
      myMessage = message;
      myFamilyName = familyName;
      myAdequateValue = adequateValue;
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return myMessage;
    }

    @Override
    public @NotNull String getFamilyName() {
      return myFamilyName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final XmlAttributeValue value = (XmlAttributeValue)descriptor.getPsiElement();
      if (value == null) return;
      try {
        final PsiReference[] refs = value.getReferences();
        if (refs.length != 1) return;
        ElementManipulators.handleContentChange(value, refs[0].getRangeInElement(), myAdequateValue);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
        XmlTag parent = attribute.getParent();
        if (!(parent instanceof HtmlTag) || XmlHighlightVisitor.isInjectedWithoutValidation(parent)) {
          return;
        }

        String name = attribute.getName();

        if (HtmlReferenceProvider.SizeReference.HEIGHT_ATTR_NAME.equalsIgnoreCase(name) ||
          HtmlReferenceProvider.SizeReference.WIDTH_ATTR_NAME.equalsIgnoreCase(name)) {
          final XmlAttributeValue value = attribute.getValueElement();

          if (value != null && value.getTextLength() > 0) {
            final PsiReference[] refs = value.getReferences();
            final boolean isHeight = HtmlReferenceProvider.SizeReference.HEIGHT_ATTR_NAME.equalsIgnoreCase(name);

            if (refs.length == 1 &&
                refs[0] instanceof HtmlReferenceProvider.SizeReference sizeReference &&
                refs[0].getRangeInElement().getLength() >= 2 // avoid errors on concatenations
              ) {
              PsiElement element = sizeReference.resolve();

              if (element == null) {
                final ImageInfoReader.Info imageInfo = sizeReference.getImageInfo();
                if (imageInfo == null || imageInfo.height == 0 || imageInfo.width == 0 || imageInfo.isSvg()) {
                  return;
                }

                final String adequateValue = String.valueOf(isHeight ? imageInfo.height : imageInfo.width);
                final String message = HtmlToolsBundle.message(
                  isHeight ? "html.inspections.check.image.height.fix.message" : "html.inspections.check.image.width.fix.message",
                  adequateValue);
                final MyFix fix = new MyFix(message, HtmlToolsBundle.message("html.inspections.check.image.fix.family"), adequateValue);
                holder.registerProblem(value,
                                       HtmlToolsBundle.message(
                                         isHeight ? "html.inspections.check.image.height.message" : "html.inspections.check.image.width.message",
                                         adequateValue),
                                       fix);
              }
            }
          }
        }
      }
    };
  }

  @Override
  public @NotNull @NonNls String getShortName() {
    return "CheckImageSize";
  }
}
