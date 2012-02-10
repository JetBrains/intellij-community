package org.jetbrains.android;

import com.android.resources.ResourceFolderType;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidUsagesTargetProvider implements UsageTargetProvider {
  @Override
  public UsageTarget[] getTargets(Editor editor, PsiFile file) {
    if (editor == null || file == null) {
      return UsageTarget.EMPTY_ARRAY;
    }

    final XmlTag tag = findValueResourceTagInContext(editor, file);
    return tag != null
           ? new UsageTarget[]{new PsiElement2UsageTargetAdapter(tag)}
           : UsageTarget.EMPTY_ARRAY;
  }

  @Override
  public UsageTarget[] getTargets(PsiElement psiElement) {
    return UsageTarget.EMPTY_ARRAY;
  }

  @Nullable
  static XmlTag findValueResourceTagInContext(@NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof XmlFile)) {
      return null;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }

    if (!AndroidResourceUtil.isInResourceSubdirectory(file, ResourceFolderType.VALUES.getName())) {
      return null;
    }

    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) {
      return null;
    }

    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (tag == null) {
      return null;
    }

    final String name = tag.getAttributeValue("name");
    if (name == null || name.length() == 0) {
      return null;
    }

    final XmlTag parentTag = tag.getParentTag();
    return parentTag != null && parentTag.getParentTag() == null ? tag : null;
  }
}
