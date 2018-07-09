/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.impl.AppEngineSdkUtil;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModelListener;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.appengine.model.PersistenceApi;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineSupportProvider extends FrameworkSupportInModuleProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.facet.AppEngineSupportProvider");
  public static final String JPA_FRAMEWORK_ID = "facet:jpa";

  @NotNull
  @Override
  public FrameworkTypeEx getFrameworkType() {
    return AppEngineFrameworkType.getFrameworkType();
  }

  @Override
  public List<FrameworkDependency> getDependenciesFrameworkIds() {
    return AppEngineWebIntegration.getInstance().getAppEngineFrameworkDependencies();
  }

  @Override
  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }

  @Override
  public boolean isSupportAlreadyAdded(@NotNull Module module, @NotNull FacetsProvider facetsProvider) {
    return !facetsProvider.getFacetsByType(module, AppEngineFacet.ID).isEmpty();
  }

  @Nullable
  public static VirtualFile createFileFromTemplate(final String templateName, final VirtualFile parent, final String fileName) {
    parent.refresh(false, false);
    final FileTemplate template = FileTemplateManager.getDefaultInstance().getJ2eeTemplate(templateName);
    try {
      final String text = template.getText(FileTemplateManager.getDefaultInstance().getDefaultProperties());
      VirtualFile file = parent.findChild(fileName);
      if (file == null) {
        file = parent.createChildData(AppEngineSupportProvider.class, fileName);
      }
      VfsUtil.saveText(file, text);
      return file;
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  private void addSupport(final Module module,
                          final ModifiableRootModel rootModel,
                          FrameworkSupportModel frameworkSupportModel,
                          String sdkPath,
                          @Nullable PersistenceApi persistenceApi) {
    FacetType<AppEngineFacet, AppEngineFacetConfiguration> facetType = AppEngineFacet.getFacetType();
    AppEngineFacet appEngineFacet = FacetManager.getInstance(module).addFacet(facetType, facetType.getDefaultFacetName(), null);
    AppEngineWebIntegration webIntegration = AppEngineWebIntegration.getInstance();
    webIntegration.registerFrameworkInModel(frameworkSupportModel, appEngineFacet);
    final AppEngineFacetConfiguration facetConfiguration = appEngineFacet.getConfiguration();
    facetConfiguration.setSdkHomePath(sdkPath);
    final AppEngineSdk sdk = appEngineFacet.getSdk();
    final Artifact webArtifact = findOrCreateWebArtifact(appEngineFacet);

    final VirtualFile webDescriptorDir = webIntegration.suggestParentDirectoryForAppEngineWebXml(module, rootModel);
    if (webDescriptorDir != null) {
      VirtualFile descriptor = createFileFromTemplate(AppEngineTemplateGroupDescriptorFactory.APP_ENGINE_WEB_XML_TEMPLATE, webDescriptorDir,
                                                      AppEngineUtil.APP_ENGINE_WEB_XML_NAME);
      if (descriptor != null) {
        webIntegration.addDescriptor(webArtifact, module.getProject(), descriptor);
      }
    }

    final Project project = module.getProject();
    webIntegration.addDevServerToModuleDependencies(rootModel, sdk);

    final Library apiJar = addProjectLibrary(module, "AppEngine API", sdk.getUserLibraryPaths(), VirtualFile.EMPTY_ARRAY);
    rootModel.addLibraryEntry(apiJar);
    webIntegration.addLibraryToArtifact(apiJar, webArtifact, project);

    if (persistenceApi != null) {
      facetConfiguration.setRunEnhancerOnMake(true);
      facetConfiguration.setPersistenceApi(persistenceApi);
      facetConfiguration.getFilesToEnhance().addAll(AppEngineUtil.getDefaultSourceRootsToEnhance(rootModel));
      try {
        final VirtualFile[] sourceRoots = rootModel.getSourceRoots();
        final VirtualFile sourceRoot;
        if (sourceRoots.length > 0) {
          sourceRoot = sourceRoots[0];
        }
        else {
          sourceRoot = findOrCreateChildDirectory(rootModel.getContentRoots()[0], "src");
        }
        VirtualFile metaInf = findOrCreateChildDirectory(sourceRoot, "META-INF");
        if (persistenceApi == PersistenceApi.JDO || persistenceApi == PersistenceApi.JDO3) {
          createFileFromTemplate(AppEngineTemplateGroupDescriptorFactory.APP_ENGINE_JDO_CONFIG_TEMPLATE, metaInf, AppEngineUtil.JDO_CONFIG_XML_NAME);
        }
        else {
          final VirtualFile file = createFileFromTemplate(AppEngineTemplateGroupDescriptorFactory.APP_ENGINE_JPA_CONFIG_TEMPLATE, metaInf, AppEngineUtil.JPA_CONFIG_XML_NAME);
          if (file != null) {
            webIntegration.setupJpaSupport(module, file);
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
      final Library library = addProjectLibrary(module, "AppEngine ORM", Collections.singletonList(sdk.getOrmLibDirectoryPath()), sdk.getOrmLibSources());
      rootModel.addLibraryEntry(library);
      webIntegration.addLibraryToArtifact(library, webArtifact, project);
    }
  }

  @NotNull
  private static Artifact findOrCreateWebArtifact(AppEngineFacet appEngineFacet) {
    Module module = appEngineFacet.getModule();
    ArtifactType webArtifactType = AppEngineWebIntegration.getInstance().getAppEngineWebArtifactType();
    final Collection<Artifact> artifacts = ArtifactUtil.getArtifactsContainingModuleOutput(module);
    for (Artifact artifact : artifacts) {
      if (webArtifactType.equals(artifact.getArtifactType())) {
        return artifact;
      }
    }
    ArtifactManager artifactManager = ArtifactManager.getInstance(module.getProject());
    PackagingElementFactory elementFactory = PackagingElementFactory.getInstance();
    ArtifactRootElement<?> root = elementFactory.createArtifactRootElement();
    elementFactory.getOrCreateDirectory(root, "WEB-INF/classes").addOrFindChild(elementFactory.createModuleOutput(module));
    return artifactManager.addArtifact(module.getName(), webArtifactType, root);
  }

  private static Library addProjectLibrary(final Module module, final String name, final List<String> jarDirectories, final VirtualFile[] sources) {
    return WriteAction.computeAndWait(() -> {
      final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(module.getProject());
      Library library = libraryTable.getLibraryByName(name);
      if (library == null) {
        library = libraryTable.createLibrary(name);
        final Library.ModifiableModel model = library.getModifiableModel();
        for (String path : jarDirectories) {
          String url = VfsUtilCore.pathToUrl(path);
          VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
          model.addJarDirectory(url, false);
        }
        for (VirtualFile sourceRoot : sources) {
          model.addRoot(sourceRoot, OrderRootType.SOURCES);
        }
        model.commit();
      }
      return library;
    });
  }

  private VirtualFile findOrCreateChildDirectory(VirtualFile parent, final String name) throws IOException {
    VirtualFile child = parent.findChild(name);
    if (child != null) {
      return child;
    }
    return parent.createChildDirectory(this, name);
  }

  @NotNull
  @Override
  public FrameworkSupportInModuleConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return new AppEngineSupportConfigurable(model);
  }

  @TestOnly
  public static void setSdkPath(FrameworkSupportInModuleConfigurable configurable, String path) {
    ((AppEngineSupportConfigurable)configurable).mySdkEditor.setPath(path);
  }

  private class AppEngineSupportConfigurable extends FrameworkSupportInModuleConfigurable implements FrameworkSupportModelListener {
    private final FrameworkSupportModel myFrameworkSupportModel;
    private JPanel myMainPanel;
    private final AppEngineSdkEditor mySdkEditor;
    private JComboBox myPersistenceApiComboBox;
    private JPanel mySdkPanel;
    private final HyperlinkLabel myErrorLabel;
    private JPanel myErrorPanel;

    private AppEngineSupportConfigurable(FrameworkSupportModel model) {
      myFrameworkSupportModel = model;
      mySdkEditor = new AppEngineSdkEditor(model.getProject());
      mySdkPanel.add(LabeledComponent.create(mySdkEditor.getMainComponent(), "Google App Engine SDK:"), BorderLayout.CENTER);
      PersistenceApiComboboxUtil.setComboboxModel(myPersistenceApiComboBox, true);
      if (model.isFrameworkSelected(JPA_FRAMEWORK_ID)) {
        myPersistenceApiComboBox.setSelectedItem(PersistenceApi.JPA.getDisplayName());
      }
      model.addFrameworkListener(this);

      myErrorLabel = new HyperlinkLabel();
      myErrorLabel.setIcon(AllIcons.RunConfigurations.ConfigurationWarning);
      myErrorLabel.setVisible(false);
      myErrorLabel.setHyperlinkTarget(AppEngineSdkUtil.APP_ENGINE_DOWNLOAD_URL);
      myErrorPanel.add(BorderLayout.CENTER, myErrorLabel);

      final Component component = mySdkEditor.getComboBox().getEditor().getEditorComponent();
      if (component instanceof JTextComponent) {
        ((JTextComponent)component).getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          protected void textChanged(DocumentEvent e) {
            checkSdk();
          }
        });
      }
      checkSdk();
    }

    private void checkSdk() {
      final String path = mySdkEditor.getPath();
      if (StringUtil.isEmptyOrSpaces(path)) {
        myErrorLabel.setVisible(true);
        myErrorLabel.setHyperlinkText("App Engine SDK path not specified. ", "Download", "");
        myMainPanel.repaint();
        return;
      }

      final ValidationResult result = AppEngineSdkUtil.checkPath(path);
      myErrorLabel.setVisible(!result.isOk());
      if (!result.isOk()) {
        myErrorLabel.setText("App Engine SDK path is not correct");
      }
      myMainPanel.repaint();
    }

    public void frameworkSelected(@NotNull FrameworkSupportProvider provider) {
      if (provider.getId().equals(JPA_FRAMEWORK_ID)) {
        myPersistenceApiComboBox.setSelectedItem(PersistenceApi.JPA.getDisplayName());
      }
    }

    public void frameworkUnselected(@NotNull FrameworkSupportProvider provider) {
      if (provider.getId().equals(JPA_FRAMEWORK_ID)) {
        myPersistenceApiComboBox.setSelectedItem(PersistenceApiComboboxUtil.NONE_ITEM);
      }
    }

    @Override
    public void wizardStepUpdated() {
    }

    @Override
    public void addSupport(@NotNull Module module,
                           @NotNull ModifiableRootModel rootModel,
                           @NotNull ModifiableModelsProvider modifiableModelsProvider) {
      AppEngineSupportProvider.this.addSupport(module, rootModel, myFrameworkSupportModel, mySdkEditor.getPath(), PersistenceApiComboboxUtil.getSelectedApi(myPersistenceApiComboBox));
    }

    @Nullable
    @Override
    public JComponent createComponent() {
      return myMainPanel;
    }
  }
}
