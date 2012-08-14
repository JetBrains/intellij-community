package org.jetbrains.android.inspections;

import com.android.AndroidConstants;
import com.android.resources.ResourceType;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
public class CreateValueResourceQuickFix implements LocalQuickFix, IntentionAction, HighPriorityAction {
  private final AndroidFacet myFacet;
  private final ResourceType myResourceType;
  private final String myResourceName;
  private final PsiFile myFile;
  private final boolean myChooseName;
  private final VirtualFile myDefaultFileToCreate;

  public CreateValueResourceQuickFix(@NotNull AndroidFacet facet,
                                       @NotNull ResourceType resourceType,
                                       @NotNull String resourceName,
                                       @NotNull PsiFile file,
                                       boolean chooseName) {
    this(facet, resourceType, resourceName, file, chooseName, null);
  }

  public CreateValueResourceQuickFix(@NotNull AndroidFacet facet,
                                     @NotNull ResourceType resourceType,
                                     @NotNull String resourceName,
                                     @NotNull PsiFile file,
                                     boolean chooseName,
                                     @Nullable VirtualFile defaultFileToCreate) {
    myFacet = facet;
    myResourceType = resourceType;
    myResourceName = resourceName;
    myFile = file;
    myChooseName = chooseName;
    myDefaultFileToCreate = defaultFileToCreate;
  }

  @NotNull
  public String getName() {
    return AndroidBundle.message("create.value.resource.quickfix.name", myResourceName,
                                 AndroidResourceUtil.getDefaultResourceFileName(myResourceType));
  }

  @NotNull
  @Override
  public String getText() {
    return AndroidBundle.message("create.value.resource.intention.name", myResourceType, myResourceName);
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
    doInvoke();
  }

  protected boolean doInvoke() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final String fileName = AndroidResourceUtil.getDefaultResourceFileName(myResourceType);
      assert fileName != null;

      if (!AndroidResourceUtil.createValueResource(myFacet.getModule(), myResourceName, myResourceType, fileName,
                                                   Collections.singletonList(AndroidConstants.FD_RES_VALUES), "a")) {
        return false;
      }
    }
    else {
      final String value = myResourceType == ResourceType.STYLEABLE ||
                           myResourceType == ResourceType.ATTR ? "\n" : null;
      final CreateXmlResourceDialog dialog =
        new CreateXmlResourceDialog(myFacet.getModule(), myResourceType, myResourceName, value, myChooseName, myDefaultFileToCreate);
      dialog.setTitle("New " + StringUtil.capitalize(myResourceType.getDisplayName()) + " Value Resource");
      dialog.show();

      if (!dialog.isOK()) {
        return false;
      }

      final Module moduleToPlaceResource = dialog.getModule();
      if (moduleToPlaceResource == null) {
        return false;
      }
      final String fileName = dialog.getFileName();
      final List<String> dirNames = dialog.getDirNames();
      final String resValue = dialog.getValue();
      final String resName = dialog.getResourceName();
      if (!AndroidResourceUtil.createValueResource(moduleToPlaceResource, resName, myResourceType, fileName, dirNames, resValue)) {
        return false;
      }
    }
    PsiDocumentManager.getInstance(myFile.getProject()).commitAllDocuments();
    UndoUtil.markPsiFileForUndo(myFile);
    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    // todo: implement local fix
  }
}
