package org.jetbrains.android.inspections;

import com.android.resources.ResourceType;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.android.dom.AndroidAnyTagDescriptor;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidElementNotAllowedInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.inspections.group.name");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("android.inspections.element.not.allowed.name");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "AndroidElementNotAllowed";
  }

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof XmlFile)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    if (AndroidUnknownAttributeInspection.isMyFile(facet, file)) {
      final String resourceType = facet.getLocalResourceManager().getFileResourceType(file);

      if (ResourceType.XML.getName().equals(resourceType)) {
        final XmlTag rootTag = ((XmlFile)file).getRootTag();

        if (rootTag == null || !AndroidXmlResourcesUtil.isSupportedRootTag(facet, rootTag.getName())) {
          return ProblemDescriptor.EMPTY_ARRAY;
        }
      }

      MyVisitor visitor = new MyVisitor(manager, isOnTheFly);
      file.accept(visitor);
      return visitor.myResult.toArray(new ProblemDescriptor[visitor.myResult.size()]);
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }

  private static class MyVisitor extends XmlRecursiveElementVisitor {
    private final InspectionManager myInspectionManager;
    private final boolean myOnTheFly;
    final List<ProblemDescriptor> myResult = new ArrayList<ProblemDescriptor>();

    private MyVisitor(InspectionManager inspectionManager, boolean onTheFly) {
      myInspectionManager = inspectionManager;
      myOnTheFly = onTheFly;
    }

    @Override
    public void visitXmlTag(XmlTag tag) {
      super.visitXmlTag(tag);

      if (tag.getNamespace().isEmpty()) {
        final XmlElementDescriptor descriptor = tag.getDescriptor();

        if (descriptor instanceof AndroidAnyTagDescriptor) {
          final XmlToken startTagNameElement = XmlTagUtil.getStartTagNameElement(tag);
          if (startTagNameElement != null) {
            myResult.add(myInspectionManager.createProblemDescriptor(startTagNameElement, XmlErrorMessages.message(
              "element.is.not.allowed.here", tag.getName()), myOnTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }

          final XmlToken endTagNameElement = XmlTagUtil.getEndTagNameElement(tag);
          if (endTagNameElement != null) {
            myResult.add(myInspectionManager.createProblemDescriptor(endTagNameElement, XmlErrorMessages.message(
              "element.is.not.allowed.here", tag.getName()), myOnTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
      }
    }
  }
}
