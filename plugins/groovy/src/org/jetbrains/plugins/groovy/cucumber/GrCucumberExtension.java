package org.jetbrains.plugins.groovy.cucumber;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.cucumber.CucumberJvmExtensionPoint;
import org.jetbrains.plugins.cucumber.StepDefinitionCreator;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition;
import org.jetbrains.plugins.cucumber.steps.CucumberStepsIndex;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.cucumber.steps.GrStepDefinition;
import org.jetbrains.plugins.groovy.cucumber.steps.GrStepDefinitionCreator;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GrCucumberExtension implements CucumberJvmExtensionPoint {
  @Override
  public boolean isStepLikeFile(@NotNull PsiElement child, @NotNull PsiElement parent) {
    return child instanceof GroovyFile && ((GroovyFile)child).getName().endsWith(".groovy");
  }

  @NotNull
  @Override
  public List<AbstractStepDefinition> getStepDefinitions(@NotNull PsiFile psiFile) {
    final List<AbstractStepDefinition> newDefs = new ArrayList<AbstractStepDefinition>();
    if (psiFile instanceof GroovyFile) {
      GrStatement[] statements = ((GroovyFile)psiFile).getStatements();
      for (GrStatement statement : statements) {
        if (GrCucumberUtil.isStepDefinition(statement)) {
          newDefs.add(new GrStepDefinition((GrMethodCall)statement));
        }
      }
    }
    return newDefs;
  }

  @NotNull
  @Override
  public FileType getStepFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  @NotNull
  @Override
  public StepDefinitionCreator getStepDefinitionCreator() {
    return new GrStepDefinitionCreator();
  }

  @NotNull
  @Override
  public String getDefaultStepFileName() {
    return "StepDef";
  }

  @Override
  public void collectAllStepDefsProviders(@NotNull List<VirtualFile> providers, @NotNull Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      if (ModuleType.get(module) instanceof JavaModuleType) {
        final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        ContainerUtil.addAll(providers, roots);
      }
    }
  }

  @Override
  public void loadStepDefinitionRootsFromLibraries(@NotNull Module module,
                                                   List<PsiDirectory> newAbstractStepDefinitionsRoots,
                                                   @NotNull Set<String> processedStepDirectories) {
    //todo

  }

  @Override
  public List<PsiElement> resolveStep(@NotNull PsiElement element) {
    final CucumberStepsIndex index = CucumberStepsIndex.getInstance(element.getProject());

    if (element instanceof GherkinStep) {
      final GherkinStep step = (GherkinStep)element;
      final List<PsiElement> result = new ArrayList<PsiElement>();
      final Set<String> substitutedNameList = step.getSubstitutedNameList();
      if (substitutedNameList.size() > 0) {
        for (String s : substitutedNameList) {
          final AbstractStepDefinition definition = index.findStepDefinition(element.getContainingFile(), s);
          if (definition != null) {
            result.add(definition.getElement());
          }
        }
        return result;
      }
    }

    return Collections.emptyList();
  }

  @Override
  public void findRelatedStepDefsRoots(Module module,
                                       PsiFile featureFile,
                                       List<PsiDirectory> newStepDefinitionsRoots,
                                       Set<String> processedStepDirectories) {
    final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (final ContentEntry contentEntry : contentEntries) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sf : sourceFolders) {
        // ToDo: check if inside test folder
        VirtualFile sfDirectory = sf.getFile();
        if (sfDirectory != null && sfDirectory.isDirectory()) {
          PsiDirectory sourceRoot = PsiDirectoryFactory.getInstance(module.getProject()).createDirectory(sfDirectory);
          if (!processedStepDirectories.contains(sourceRoot.getVirtualFile().getPath())) {
            newStepDefinitionsRoots.add(sourceRoot);
          }
        }
      }
    }
  }
}
