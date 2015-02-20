/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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

public class NewGroovyClassAction extends JavaCreateTemplateInPackageAction<GrTypeDefinition> implements DumbAware {

  public NewGroovyClassAction() {
    super(GroovyBundle.message("newclass.menu.action.text"), GroovyBundle.message("newclass.menu.action.description"),
          JetgroovyIcons.Groovy.Class, true);
  }

  @Override
  protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(GroovyBundle.message("newclass.dlg.title"))
      .addKind("Class", JetgroovyIcons.Groovy.Class, GroovyTemplates.GROOVY_CLASS)
      .addKind("Interface", JetgroovyIcons.Groovy.Interface, GroovyTemplates.GROOVY_INTERFACE);

    if (GroovyConfigUtils.getInstance().isVersionAtLeast(directory, GroovyConfigUtils.GROOVY2_3, true)) {
      builder.addKind("Trait", JetgroovyIcons.Groovy.Trait, GroovyTemplates.GROOVY_TRAIT);
    }

    builder
      .addKind("Enum", JetgroovyIcons.Groovy.Enum, GroovyTemplates.GROOVY_ENUM)
      .addKind("Annotation", JetgroovyIcons.Groovy.AnnotationType, GroovyTemplates.GROOVY_ANNOTATION);

    for (FileTemplate template : FileTemplateManager.getInstance(project).getAllTemplates()) {
      FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension());
      if (fileType.equals(GroovyFileType.GROOVY_FILE_TYPE) && JavaDirectoryService.getInstance().getPackage(directory) != null) {
        builder.addKind(template.getName(), JetgroovyIcons.Groovy.Class, template.getName());
      }
    }
  }

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    return super.isAvailable(dataContext) && LibrariesUtil.hasGroovySdk(LangDataKeys.MODULE.getData(dataContext));
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName, String templateName) {
    return GroovyBundle.message("newclass.menu.action.text");
  }

  @Override
  protected PsiElement getNavigationElement(@NotNull GrTypeDefinition createdElement) {
    return createdElement.getLBrace();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    if (!presentation.isVisible()) return;

    IdeView view = LangDataKeys.IDE_VIEW.getData(e.getDataContext());
    if (view == null) return;
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
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
