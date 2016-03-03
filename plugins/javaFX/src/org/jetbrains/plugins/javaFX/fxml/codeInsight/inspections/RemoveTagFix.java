package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

/**
 * @author Pavel.Dolgov
 */
public class RemoveTagFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#" + RemoveTagFix.class.getName());

  private final String myTagName;

  public RemoveTagFix(String name) {
    myTagName = name;
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Remove tag '" + myTagName + "'";
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Remove tag";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (element != null) {
      final PsiFile containingFile = element.getContainingFile();
      LOG.assertTrue(containingFile != null && JavaFxFileTypeFactory.isFxml(containingFile),
                     containingFile == null ? "no containing file found" : "containing file: " + containingFile.getName());
      final XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
      if (xmlTag != null) {
        final XmlTag parentTag = xmlTag.getParentTag();
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(element)) return;
        xmlTag.delete();
        if (parentTag != null) {
          CodeStyleManager.getInstance(project).reformat(parentTag);
        }
      }
    }
  }
}
