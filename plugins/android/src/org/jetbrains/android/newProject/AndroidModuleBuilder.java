/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.newProject;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.importDependencies.ImportDependenciesUtil;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.run.TargetSelectionMode;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.android.run.testing.AndroidTestRunConfigurationType;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.jetbrains.android.util.AndroidUtils.createChildDirectoryIfNotExist;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidModuleBuilder extends JavaModuleBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.newProject.AndroidModuleBuilder");

  private String myPackageName;
  private String myApplicationName;
  private String myActivityName;
  private ProjectType myProjectType;
  private Module myTestedModule;
  private Sdk mySdk;
  private TargetSelectionMode myTargetSelectionMode;
  private String myPreferredAvd;

  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);

    rootModel.setSdk(mySdk);

    final LanguageLevelModuleExtension moduleExt = rootModel.getModuleExtension(LanguageLevelModuleExtension.class);

    if (moduleExt != null) {
      LanguageLevel languageLevel = moduleExt.getLanguageLevel();
      if (languageLevel == null) {
        final LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(rootModel.getProject());
        if (projectExt != null) {
          languageLevel = projectExt.getLanguageLevel();
        }
      }
      if (languageLevel == LanguageLevel.JDK_1_3) {
        moduleExt.setLanguageLevel(LanguageLevel.JDK_1_5);
      }
    }

    VirtualFile[] files = rootModel.getContentRoots();
    if (files.length > 0) {
      final VirtualFile contentRoot = files[0];
      final AndroidFacet facet = AndroidUtils.addAndroidFacet(rootModel, contentRoot, myProjectType == ProjectType.LIBRARY);

      if (myProjectType == null) {
        ImportDependenciesUtil.importDependencies(rootModel.getModule(), true);
        return;
      }

      final Project project = rootModel.getProject();
      final VirtualFile sourceRoot = findSourceRoot(rootModel);

      if (myProjectType == ProjectType.TEST) {
        assert myTestedModule != null;
        facet.getConfiguration().PACK_TEST_CODE = true;
        ModuleOrderEntry entry = rootModel.addModuleOrderEntry(myTestedModule);
        entry.setScope(DependencyScope.PROVIDED);
      }

      StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  createProject(contentRoot, sourceRoot, facet);
                }
              });
            }
          });
        }
      });
    }
  }

  private void createProject(VirtualFile contentRoot, VirtualFile sourceRoot, AndroidFacet facet) {
    if (myProjectType == ProjectType.APPLICATION) {
      createDirectoryStructure(contentRoot, sourceRoot, facet);
    }
    else {
      createProjectByAndroidTool(contentRoot, sourceRoot, facet);
    }
  }

  private void createDirectoryStructure(VirtualFile contentRoot, VirtualFile sourceRoot, AndroidFacet facet) {
    if (isHelloAndroid()) {
      if (createProjectByAndroidTool(contentRoot, sourceRoot, facet)) {
        return;
      }
    }
    Project project = facet.getModule().getProject();
    createManifestFileAndAntFiles(project, contentRoot);
    createResourcesAndLibs(project, contentRoot);
    PsiDirectory sourceDir = sourceRoot != null ? PsiManager.getInstance(project).findDirectory(sourceRoot) : null;
    createActivityAndSetupManifest(facet, sourceDir);
    if (myTargetSelectionMode != null) {
      addRunConfiguration(facet, myTargetSelectionMode, myPreferredAvd);
    }
  }

  @NotNull
  private static String getAntProjectName(@NotNull String moduleName) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < moduleName.length(); i++) {
      char c = moduleName.charAt(i);
      if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || Character.isDigit(c))) {
        c = '_';
      }
      result.append(c);
    }
    return result.toString();
  }

  private boolean createProjectByAndroidTool(final VirtualFile contentRoot,
                                             final VirtualFile sourceRoot,
                                             final AndroidFacet facet) {


    File tempContentRoot = null;

    // todo: support custom non-empty source root

    if (sourceRoot != null &&
        sourceRoot.getChildren().length == 0 &&
        (sourceRoot.getParent() != contentRoot || !SdkConstants.FD_SOURCES.equals(sourceRoot.getName()))) {
      try {
        tempContentRoot = FileUtil.createTempDirectory("android_temp_content_root", "tmp");
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    final Module module = facet.getModule();
    AndroidPlatform platform = AndroidPlatform.parse(mySdk);

    if (platform == null) {
      Messages.showErrorDialog(module.getProject(), "Cannot parse Android SDK", CommonBundle.getErrorTitle());
      return true;
    }

    final IAndroidTarget target = platform.getTarget();

    final String androidToolPath =
      platform.getSdkData().getLocation() + File.separator + AndroidCommonUtils.toolPath(SdkConstants.androidCmdName());

    if (!new File(androidToolPath).exists()) {
      return false;
    }

    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(FileUtil.toSystemDependentName(androidToolPath));

    commandLine.addParameter("create");

    switch (myProjectType) {
      case APPLICATION:
        commandLine.addParameter("project");
        break;
      case LIBRARY:
        commandLine.addParameter("lib-project");
        break;
      case TEST:
        commandLine.addParameter("test-project");
        break;
    }

    commandLine.addParameters("--name");
    commandLine.addParameter(getAntProjectName(module.getName()));

    commandLine.addParameters("--path");
    final String targetDirectoryPath = tempContentRoot != null ? tempContentRoot.getPath() : contentRoot.getPath();
    commandLine.addParameter(FileUtil.toSystemDependentName(targetDirectoryPath));

    if (myProjectType == ProjectType.APPLICATION || myProjectType == ProjectType.LIBRARY) {
      String apiLevel = target.hashString();
      commandLine.addParameter("--target");
      commandLine.addParameter(apiLevel);
      commandLine.addParameter("--package");
      commandLine.addParameter(myPackageName);
    }

    if (myProjectType == ProjectType.APPLICATION) {
      commandLine.addParameter("--activity");
      commandLine.addParameter(myActivityName);
    }
    else if (myProjectType == ProjectType.TEST) {
      String moduleDirPath = AndroidRootUtil.getModuleDirPath(myTestedModule);
      assert moduleDirPath != null;
      commandLine.addParameter("--main");
      commandLine.addParameter(FileUtil.toSystemDependentName(moduleDirPath));
    }

    final File finalTempContentRoot = tempContentRoot;

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final Project project = module.getProject();
        AndroidUtils.runExternalTool(commandLine, null, true, project);

        if (finalTempContentRoot != null) {
          final File[] children = finalTempContentRoot.listFiles();

          if (children != null) {
            for (File child : children) {
              if (SdkConstants.FD_SOURCES.equals(child.getName())) {
                continue;
              }
              final File to = new File(contentRoot.getPath(), child.getName());

              if (!FileUtil.moveDirWithContent(child, to)) {
                LOG.error("Cannot move content from " + child.getPath() + " to " + to.getPath());
              }
            }
          }

          final File tempSourceRoot = new File(finalTempContentRoot, SdkConstants.FD_SOURCES);
          if (tempSourceRoot.exists()) {
            final File to = new File(sourceRoot.getPath());

            if (!FileUtil.moveDirWithContent(tempSourceRoot, to)) {
              LOG.error("Cannot move content from " + tempSourceRoot.getPath() + " to " + to.getPath());
            }
          }
        }

        StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
          public void run() {
            FileDocumentManager.getInstance().saveAllDocuments();
          }
        });
        contentRoot.refresh(false, true);

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {

            if (contentRoot.findChild(SdkConstants.FN_ANDROID_MANIFEST_XML) == null) {
              AndroidUtils.printMessageToConsole(project, "The project wasn't generated by 'android' tool.",
                                                 ConsoleViewContentType.ERROR_OUTPUT);
              return;
            }

            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                try {
                  if (project.isDisposed()) {
                    return;
                  }
                  if (myProjectType == ProjectType.APPLICATION) {
                    assignApplicationName(facet);
                    configureManifest(facet, target);
                    createChildDirectoryIfNotExist(project, contentRoot, SdkConstants.FD_ASSETS);
                    createChildDirectoryIfNotExist(project, contentRoot, SdkConstants.FD_NATIVE_LIBS);
                  }
                  else if (myProjectType == ProjectType.LIBRARY && myPackageName != null) {
                    final String[] dirs = myPackageName.split("\\.");
                    VirtualFile file = sourceRoot;

                    for (String dir : dirs) {
                      if (file == null || dir.length() == 0) {
                        break;
                      }
                      final VirtualFile childDir = file.findChild(dir);
                      file = childDir != null
                             ? childDir
                             : file.createChildDirectory(project, dir);
                    }
                  }
                }
                catch (IOException e) {
                  LOG.error(e);
                }
              }
            });

            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                if (project.isDisposed() || facet.getModule().isDisposed()) {
                  return;
                }

                if (myTargetSelectionMode != null) {
                  if (myProjectType == ProjectType.APPLICATION) {
                    addRunConfiguration(facet, myTargetSelectionMode, myPreferredAvd);
                  }
                  else if (myProjectType == ProjectType.TEST) {
                    addTestRunConfiguration(facet, myTargetSelectionMode, myPreferredAvd);
                  }
                }
              }
            });

            new ReformatCodeProcessor(project, module, false).run();
          }
        });
      }
    });
    return true;
  }

  private static void configureManifest(@NotNull AndroidFacet facet, @NotNull IAndroidTarget target) {
    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return;
    }

    final XmlTag manifestTag = manifest.getXmlTag();
    if (manifestTag == null) {
      return;
    }

    XmlTag usesSdkTag = manifestTag.createChildTag("uses-sdk", "", null, false);
    if (usesSdkTag != null) {
      usesSdkTag = manifestTag.addSubTag(usesSdkTag, true);
      usesSdkTag.setAttribute("minSdkVersion", SdkConstants.NS_RESOURCES, target.getVersion().getApiString());
    }

    final PsiFile manifestFile = manifestTag.getContainingFile();
    if (manifestFile != null) {
      CodeStyleManager.getInstance(manifestFile.getProject()).reformat(manifestFile);
    }
  }

  private void assignApplicationName(AndroidFacet facet) {
    if (myApplicationName == null || myApplicationName.length() == 0) {
      return;
    }

    final LocalResourceManager manager = facet.getLocalResourceManager();
    ResourceElement appNameResElement = null;
    final String appNameResource = "app_name";

    for (ResourceElement resElement : manager.getValueResources("string")) {
      if (appNameResource.equals(resElement.getName().getValue())) {
        appNameResElement = resElement;
      }
    }

    final String normalizedAppName = AndroidResourceUtil.normalizeXmlResourceValue(myApplicationName.replace("\\", "\\\\"));

    if (appNameResElement == null) {
      manager.addValueResource("string", appNameResource, normalizedAppName);
    }
    else {
      appNameResElement.setStringValue(normalizedAppName);
    }

    final Manifest manifest = facet.getManifest();

    if (manifest != null) {
      manifest.getApplication().getLabel().setValue(ResourceValue.referenceTo('@', null, "string", appNameResource));
      }
    }

  private void createManifestFileAndAntFiles(Project project, VirtualFile contentRoot) {
    VirtualFile existingManifestFile = contentRoot.findChild(SdkConstants.FN_ANDROID_MANIFEST_XML);
    if (existingManifestFile != null) {
      return;
    }
    try {
      AndroidFileTemplateProvider.createFromTemplate(project, contentRoot, AndroidFileTemplateProvider.ANDROID_MANIFEST_TEMPLATE,
                                                     SdkConstants.FN_ANDROID_MANIFEST_XML);

      AndroidPlatform platform = AndroidPlatform.parse(mySdk);

      if (platform == null) {
        Messages.showErrorDialog(project, "Cannot parse Android SDK: 'default.properties' won't be generated", CommonBundle.getErrorTitle());
        return;
      }

      Properties properties = FileTemplateManager.getInstance().getDefaultProperties();
      properties.setProperty("TARGET", platform.getTarget().hashString());
      AndroidFileTemplateProvider.createFromTemplate(project, contentRoot, AndroidFileTemplateProvider.DEFAULT_PROPERTIES_TEMPLATE,
                                                     SdkConstants.FN_PROJECT_PROPERTIES, properties);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void addRunConfiguration(@NotNull AndroidFacet facet,
                                   @NotNull TargetSelectionMode targetSelectionMode,
                                   @Nullable String targetAvd) {
    String activityClass;
    if (isHelloAndroid()) {
      activityClass = myPackageName + '.' + myActivityName;
    }
    else {
      activityClass = null;
    }
    Module module = facet.getModule();
    AndroidUtils.addRunConfiguration(facet, activityClass, false, targetSelectionMode, targetAvd);
  }

  private static void addTestRunConfiguration(final AndroidFacet facet, @NotNull TargetSelectionMode mode, @Nullable String preferredAvd) {
    Project project = facet.getModule().getProject();
    RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    Module module = facet.getModule();
    RunnerAndConfigurationSettings settings = runManager
      .createRunConfiguration(module.getName(), AndroidTestRunConfigurationType.getInstance().getFactory());

    AndroidTestRunConfiguration configuration = (AndroidTestRunConfiguration)settings.getConfiguration();
    configuration.setModule(module);
    configuration.setTargetSelectionMode(mode);
    if (preferredAvd != null) {
      configuration.PREFERRED_AVD = preferredAvd;
    }

    runManager.addConfiguration(settings, false);
    runManager.setActiveConfiguration(settings);
  }

  private boolean isHelloAndroid() {
    return myActivityName.length() > 0;
  }

  @Nullable
  private static VirtualFile findSourceRoot(ModifiableRootModel model) {
    VirtualFile genSourceRoot = AndroidRootUtil.getStandartGenDir(model.getModule());
    for (VirtualFile root : model.getSourceRoots()) {
      if (root != genSourceRoot) {
        return root;
      }
    }
    return null;
  }

  @Nullable
  private static PsiDirectory createPackageIfPossible(final PsiDirectory sourceDir, String packageName) {
    if (sourceDir != null) {
      final String[] ids = packageName.split("\\.");
      return ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
        public PsiDirectory compute() {
          PsiDirectory dir = sourceDir;
          for (String id : ids) {
            PsiDirectory child = dir.findSubdirectory(id);
            dir = child == null ? dir.createSubdirectory(id) : child;
          }
          return dir;
        }
      });
    }
    return null;
  }

  private void createActivityAndSetupManifest(final AndroidFacet facet, final PsiDirectory sourceDir) {
    if (myPackageName != null) {
      CommandProcessor.getInstance().executeCommand(facet.getModule().getProject(), new ExternalChangeAction() {
        public void run() {
          Runnable action = new Runnable() {
            public void run() {
              PsiDirectory packageDir = createPackageIfPossible(sourceDir, myPackageName);
              if (packageDir == null) return;
              final Manifest manifest = facet.getManifest();
              if (manifest != null) {
                manifest.getPackage().setValue(myPackageName);
                final Project project = facet.getModule().getProject();
                StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
                  public void run() {
                    FileDocumentManager.getInstance().saveAllDocuments();
                  }
                });

                assignApplicationName(facet);

                final AndroidPlatform platform = AndroidPlatform.parse(mySdk);
                if (platform != null) {
                  configureManifest(facet, platform.getTarget());
                }
              }
            }
          };
          ApplicationManager.getApplication().runWriteAction(action);
        }
      }, AndroidBundle.message("build.android.module.process.title"), null);
    }
  }

  private void createResourcesAndLibs(final Project project, final VirtualFile rootDir) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          createChildDirectoryIfNotExist(project, rootDir, SdkConstants.FD_ASSETS);
          createChildDirectoryIfNotExist(project, rootDir, SdkConstants.FD_NATIVE_LIBS);
          VirtualFile resDir = createChildDirectoryIfNotExist(project, rootDir, SdkConstants.FD_RES);
          VirtualFile drawableDir = createChildDirectoryIfNotExist(project, resDir, SdkConstants.FD_RES_DRAWABLE);
          createFileFromResource(project, drawableDir, "icon.png", "/icons/androidLarge.png");
          if (isHelloAndroid()) {
            VirtualFile valuesDir = createChildDirectoryIfNotExist(project, resDir, SdkConstants.FD_RES_VALUES);
            createFileFromResource(project, valuesDir, "strings.xml", "res/values/strings.xml");
            VirtualFile layoutDir = AndroidUtils.createChildDirectoryIfNotExist(project, resDir, SdkConstants.FD_RES_LAYOUT);
            createFileFromResource(project, layoutDir, "main.xml", "res/layout/main.xml");
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  private static void createFileFromResource(Project project, VirtualFile drawableDir, String name, String resourceFilePath)
    throws IOException {
    if (drawableDir.findChild(name) != null) {
      return;
    }
    VirtualFile resFile = drawableDir.createChildData(project, name);
    InputStream stream = AndroidModuleBuilder.class.getResourceAsStream(resourceFilePath);
    try {
      byte[] bytes = FileUtil.adaptiveLoadBytes(stream);
      resFile.setBinaryContent(bytes);
    }
    finally {
      stream.close();
    }
  }

  public void setProjectType(ProjectType projectType) {
    myProjectType = projectType;
  }

  public void setActivityName(String activityName) {
    myActivityName = activityName;
  }

  public void setApplicationName(String applicationName) {
    myApplicationName = applicationName;
  }

  public void setPackageName(String packageName) {
    myPackageName = packageName;
  }

  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }

  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  public void setTestedModule(Module module) {
    myTestedModule = module;
  }

  public void setTargetSelectionMode(TargetSelectionMode targetSelectionMode) {
    myTargetSelectionMode = targetSelectionMode;
  }

  public void setPreferredAvd(String preferredAvd) {
    myPreferredAvd = preferredAvd;
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, ModulesProvider modulesProvider) {
    List<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    ProjectWizardStepFactory factory = ProjectWizardStepFactory.getInstance();
    steps.add(factory.createSourcePathsStep(wizardContext, this, null, "reference.dialogs.new.project.fromScratch.source"));

    if (!hasAppropriateJdk()) {
      steps.add(new ProjectJdkForModuleStep(wizardContext, JavaSdk.getInstance()) {
        @Override
        public void updateDataModel() {
          // do nothing
        }

        @Override
        public boolean validate() {
          for (Object o : getAllJdks()) {
            if (o instanceof Sdk) {
              Sdk sdk = (Sdk)o;
              if (AndroidSdkUtils.isApplicableJdk(sdk)) {
                return true;
              }
            }
          }
          Messages.showErrorDialog(AndroidBundle.message("no.jdk.error"), CommonBundle.getErrorTitle());
          return false;
        }
      });
    }

    steps.add(new AndroidModuleWizardStep(this, wizardContext));
    return steps.toArray(new ModuleWizardStep[steps.size()]);
  }

  public Icon getBigIcon() {
    return AndroidUtils.ANDROID_ICON_24;
  }

  public String getDescription() {
    return AndroidBundle.message("android.module.type.description");
  }

  public String getPresentableName() {
    return AndroidBundle.message("android.module.type.name");
  }

  @Override
  public String getBuilderId() {
    return getClass().getName();
  }

  private static boolean hasAppropriateJdk() {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (AndroidSdkUtils.isApplicableJdk(sdk)) {
        return true;
      }
    }
    return false;
  }
}
