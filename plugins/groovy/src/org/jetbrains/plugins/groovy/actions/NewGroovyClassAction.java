// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.JavaCreateTemplateInPackageAction;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import static org.jetbrains.plugins.groovy.projectRoots.RootTypesKt.ROOT_TYPES;

public class NewGroovyClassAction extends JavaCreateTemplateInPackageAction<GrTypeDefinition> implements DumbAware, UpdateInBackground {

  public NewGroovyClassAction() {
    super(GroovyBundle.message("new.class.action.text"), GroovyBundle.message("new.class.action.description"),
          JetgroovyIcons.Groovy.Class, ROOT_TYPES);
  }

  @Override
  protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(GroovyBundle.message("new.class.dialog.title"))
      .addKind(GroovyBundle.message("new.class.list.item.class"), JetgroovyIcons.Groovy.Class, GroovyTemplates.GROOVY_CLASS)
      .addKind(GroovyBundle.message("new.class.list.item.interface"), JetgroovyIcons.Groovy.Interface, GroovyTemplates.GROOVY_INTERFACE);

    if (GroovyConfigUtils.getInstance().isVersionAtLeast(directory, GroovyConfigUtils.GROOVY2_3, true)) {
      builder.addKind(GroovyBundle.message("new.class.list.item.trait"), JetgroovyIcons.Groovy.Trait, GroovyTemplates.GROOVY_TRAIT);
    }

    builder
      .addKind(GroovyBundle.message("new.class.list.item.enum"), JetgroovyIcons.Groovy.Enum, GroovyTemplates.GROOVY_ENUM)
      .addKind(GroovyBundle.message("new.class.list.item.annotation"), JetgroovyIcons.Groovy.AnnotationType, GroovyTemplates.GROOVY_ANNOTATION);

    if (GroovyConfigUtils.isAtLeastGroovy40(directory)) {
      builder.addKind(GroovyBundle.message("new.class.list.item.record"), JetgroovyIcons.Groovy.Record, GroovyTemplates.GROOVY_RECORD);
    }

    for (FileTemplate template : FileTemplateManager.getInstance(project).getAllTemplates()) {
      FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension());
      if (fileType.equals(GroovyFileType.GROOVY_FILE_TYPE) && JavaDirectoryService.getInstance().getPackage(directory) != null) {
        builder.addKind(template.getName(), JetgroovyIcons.Groovy.Class, template.getName());
      }
    }

    builder.setValidator(new InputValidatorEx() {

      @Override
      public String getErrorText(String inputString) { return GroovyBundle.message("invalid.qualified.name"); }

      @Override
      public boolean checkInput(String inputString) { return true; }

      @Override
      public boolean canClose(String inputString) {
        return !StringUtil.isEmptyOrSpaces(inputString) && PsiNameHelper.getInstance(project).isQualifiedName(inputString);
      }
    });
  }

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    return super.isAvailable(dataContext) && LibrariesUtil.hasGroovySdk(PlatformCoreDataKeys.MODULE.getData(dataContext));
  }

  @Override
  protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
    return GroovyBundle.message("new.class.action.text");
  }

  @Override
  protected PsiElement getNavigationElement(@NotNull GrTypeDefinition createdElement) {
    return createdElement.getLBrace();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    if (!presentation.isVisible()) return;

    IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    if (view == null) return;
    Project project = e.getProject();
    if (project == null) return;

    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiDirectory dir : view.getDirectories()) {
      if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && checkPackageExists(dir)) {
        for (GroovySourceFolderDetector detector : GroovySourceFolderDetector.EP_NAME.getExtensions()) {
          if (detector.isGroovySourceFolder(dir)) {
            presentation.setWeight(Presentation.HIGHER_WEIGHT);
            break;
          }
        }
        return;
      }
    }
  }

  @Override
  protected final GrTypeDefinition doCreate(PsiDirectory dir, String className, String templateName) throws IncorrectOperationException {
    final String fileName = className + NewGroovyActionBase.GROOVY_EXTENSION;
    final PsiFile fromTemplate = GroovyTemplatesFactory.createFromTemplate(dir, className, fileName, templateName, true);
    if (fromTemplate instanceof GroovyFile) {
      CodeStyleManager.getInstance(fromTemplate.getManager()).reformat(fromTemplate);
      return ((GroovyFile)fromTemplate).getTypeDefinitions()[0];
    }
    final String description = fromTemplate.getFileType().getDescription();
    throw new IncorrectOperationException(GroovyBundle.message("groovy.file.extension.is.not.mapped.to.groovy.file.type", description));
  }
}