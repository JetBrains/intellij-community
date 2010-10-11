package org.jetbrains.javafx.actions;

import com.intellij.facet.FacetManager;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.JavaCreateTemplateInPackageAction;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.JavaFxFileType;
import org.jetbrains.javafx.facet.JavaFxFacet;
import org.jetbrains.javafx.lang.psi.JavaFxFile;

import javax.swing.*;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class NewJavaFxScriptAction extends JavaCreateTemplateInPackageAction<JavaFxFile> implements DumbAware {
  private static final String NAME_TEMPLATE_PROPERTY = "NAME";

  private static String getFileName(final String fileName) {
    return fileName + "." + JavaFxFileType.INSTANCE.getDefaultExtension();
  }

  protected NewJavaFxScriptAction() {
    super(JavaFxBundle.message("javafx.script"), JavaFxBundle.message("create.new.javafx.script"), JavaFxFileType.INSTANCE.getIcon(), true);
  }

  @Override
  protected boolean isAvailable(final DataContext dataContext) {
    final Module module = DataKeys.MODULE.getData(dataContext);
    return super.isAvailable(dataContext) && module != null && FacetManager.getInstance(module).getFacetByType(JavaFxFacet.ID) != null;
  }

  @Override
  protected PsiElement getNavigationElement(@NotNull final JavaFxFile createdElement) {
    return createdElement;
  }

  @Override
  protected void doCheckCreate(final PsiDirectory dir, final String className, final String templateName)
    throws IncorrectOperationException {
    dir.checkCreateFile(getFileName(className));
  }

  @Override
  protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    final Icon icon = getTemplatePresentation().getIcon();
    builder.setTitle(getTemplatePresentation().getText())
      .addKind(JavaFxBundle.message("javafx.class"), icon, "JavaFX Class")
      .addKind(JavaFxBundle.message("javafx.file"), icon, "JavaFX File")
      .addKind(JavaFxBundle.message("javafx.stage"), icon, "JavaFX Stage");
  }


  @Override
  protected String getActionName(final PsiDirectory directory, final String newName, final String templateName) {
    return getTemplatePresentation().getText();
  }

  @Override
  protected JavaFxFile doCreate(final PsiDirectory directory,
                                final String className,
                                final String templateName) throws IncorrectOperationException {
    final FileTemplate template = FileTemplateManager.getInstance().getInternalTemplate(templateName);
    final Properties properties = new Properties(FileTemplateManager.getInstance().getDefaultProperties());
    JavaTemplateUtil.setPackageNameAttribute(properties, directory);
    properties.setProperty(NAME_TEMPLATE_PROPERTY, className);
    final String text;
    try {
      text = template.getText(properties);
    }
    catch (Exception e) {
      throw new RuntimeException("Unable to load template for " + FileTemplateManager.getInstance().internalTemplateToSubject(templateName),
                                 e);
    }

    final PsiFileFactory factory = PsiFileFactory.getInstance(directory.getProject());
    final PsiFile file = factory.createFileFromText(getFileName(className), text);

    return (JavaFxFile)directory.add(file);
  }
}
