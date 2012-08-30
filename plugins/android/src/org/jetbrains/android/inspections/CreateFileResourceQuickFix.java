package org.jetbrains.android.inspections;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.actions.CreateResourceFileAction;
import org.jetbrains.android.actions.CreateTypedResourceFileAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
public class CreateFileResourceQuickFix implements LocalQuickFix, IntentionAction, HighPriorityAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.CreateFileResourceQuickFix");

  private final AndroidFacet myFacet;
  private final ResourceType myResourceType;
  private final String myResourceName;
  private final PsiFile myFile;
  private final boolean myChooseResName;

  public CreateFileResourceQuickFix(@NotNull AndroidFacet facet,
                                    @NotNull ResourceType resourceType,
                                    @NotNull String resourceName,
                                    @NotNull PsiFile file,
                                    boolean chooseResName) {
    myFacet = facet;
    myResourceType = resourceType;
    myResourceName = resourceName;
    myFile = file;
    myChooseResName = chooseResName;
  }

  @NotNull
  public String getName() {
    return AndroidBundle.message("create.file.resource.quickfix.name", myResourceName,
                                 '\'' + myResourceType.getName() + "' directory");
  }

  @NotNull
  @Override
  public String getText() {
    return AndroidBundle.message("create.file.resource.intention.name", myResourceType, myResourceName + ".xml");
  }

  @NotNull
  public String getFamilyName() {
    return AndroidBundle.message("quick.fixes.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final XmlFile newFile =
      CreateResourceFileAction.createFileResource(myFacet, myResourceType, myResourceName + ".xml", null, null, myChooseResName, null);
    if (newFile != null) {
      UndoUtil.markPsiFileForUndo(myFile);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final VirtualFile resourceDir = myFacet.getLocalResourceManager().getResourceDir();
    if (resourceDir == null) {
      return;
    }
    final PsiDirectory psiResDir = PsiManager.getInstance(project).findDirectory(resourceDir);
    if (psiResDir == null) {
      return;
    }
    final String resDirName = myResourceType.getName();
    PsiDirectory resSubdir = psiResDir.findSubdirectory(resDirName);

    if (resSubdir == null) {
      resSubdir = ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
        public PsiDirectory compute() {
          return psiResDir.createSubdirectory(resDirName);
        }
      });
    }

    try {
      AndroidResourceUtil.createFileResource(myResourceName, resSubdir, CreateTypedResourceFileAction.getDefaultRootTagByResourceType(
        ResourceFolderType.getFolderType(resDirName)), resDirName, false);
      UndoUtil.markPsiFileForUndo(myFile);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }
}
