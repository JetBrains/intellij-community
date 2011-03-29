/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

public class NewGroovyClassAction extends JavaCreateTemplateInPackageAction<GrTypeDefinition> implements DumbAware {
  public static final String GROOVY_CLASS = "GroovyClass.groovy";
  public static final String GROOVY_INTERFACE = "GroovyInterface.groovy";
  public static final String GROOVY_ENUM = "GroovyEnum.groovy";
  public static final String GROOVY_ANNOTATION = "GroovyAnnotation.groovy";

  public NewGroovyClassAction() {
    super(GroovyBundle.message("newclass.menu.action.text"), GroovyBundle.message("newclass.menu.action.description"), GroovyIcons.CLASS, true);
  }

  @Override
  protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(GroovyBundle.message("newclass.dlg.title"))
      .addKind("Class", GroovyIcons.CLASS, GROOVY_CLASS)
      .addKind("Interface", GroovyIcons.INTERFACE, GROOVY_INTERFACE)
      .addKind("Enum", GroovyIcons.ENUM, GROOVY_ENUM)
      .addKind("Annotation", GroovyIcons.ANNOTATION_TYPE, GROOVY_ANNOTATION);
  }

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    return super.isAvailable(dataContext) && LibrariesUtil.hasGroovySdk(DataKeys.MODULE.getData(dataContext));
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName, String templateName) {
    return GroovyBundle.message("newclass.menu.action.text");
  }

  @Override
  protected PsiElement getNavigationElement(@NotNull GrTypeDefinition createdElement) {
    return createdElement.getLBraceGroovy();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    if (!presentation.isVisible()) return;

    IdeView view = LangDataKeys.IDE_VIEW.getData(e.getDataContext());
    if (view == null) return;
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
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

  protected final GrTypeDefinition doCreate(PsiDirectory dir, String className, String templateName) throws IncorrectOperationException {
    final String fileName = className + NewGroovyActionBase.GROOVY_EXTENSION;
    final PsiFile fromTemplate = GroovyTemplatesFactory.createFromTemplate(dir, className, fileName, templateName);
    if (fromTemplate instanceof GroovyFile) {
      return ((GroovyFile)fromTemplate).getTypeDefinitions()[0];
    }
    final String description = fromTemplate.getFileType().getDescription();
    throw new IncorrectOperationException(GroovyBundle.message("groovy.file.extension.is.not.mapped.to.groovy.file.type", description));
  }

}
