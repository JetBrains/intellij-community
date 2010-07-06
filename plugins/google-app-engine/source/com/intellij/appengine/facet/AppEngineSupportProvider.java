package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.server.instance.AppEngineServerModel;
import com.intellij.appengine.server.run.AppEngineServerConfigurationType;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.ui.FacetBasedFrameworkSupportProvider;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.util.frameworkSupport.*;
import com.intellij.javaee.JavaeePersistenceDescriptorsConstants;
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.javaee.artifact.JavaeeArtifactUtil;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.javaee.run.configuration.J2EEConfigurationFactory;
import com.intellij.javaee.web.artifact.WebArtifactUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.jpa.facet.JpaFacet;
import com.intellij.jpa.facet.JpaFacetType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.run.BuildArtifactsBeforeRunTaskProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineSupportProvider extends FacetBasedFrameworkSupportProvider<AppEngineFacet> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.facet.AppEngineSupportProvider");
  private static final String JPA_PROVIDER_ID = "facet:jpa";

  public AppEngineSupportProvider() {
    super(FacetTypeRegistry.getInstance().findFacetType(AppEngineFacet.ID));
  }

  @Override
  public String[] getPrecedingFrameworkProviderIds() {
    return new String[]{JPA_PROVIDER_ID};
  }

  protected void setupConfiguration(AppEngineFacet facet, ModifiableRootModel rootModel, FrameworkVersion version) {
    final WebFacet webFacet = facet.getWebFacet();
    final VirtualFile webXml = webFacet.getWebXmlDescriptor().getVirtualFile();
    if (webXml == null) return;

    createFileFromTemplate(AppEngineTemplateGroupDescriptorFactory.APP_ENGINE_WEB_XML_TEMPLATE, webXml.getParent(), AppEngineUtil.APP_ENGINE_WEB_XML_NAME);
  }

  @Nullable
  private VirtualFile createFileFromTemplate(final String templateName, final VirtualFile parent, final String fileName) {
    parent.refresh(false, false);
    final FileTemplate template = FileTemplateManager.getInstance().getJ2eeTemplate(templateName);
    try {
      final String text = template.getText(FileTemplateManager.getInstance().getDefaultProperties());
      VirtualFile file = parent.findChild(fileName);
      if (file == null) {
        file = parent.createChildData(this, fileName);
      }
      VfsUtil.saveText(file, text);
      return file;
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  private void addSupport(final Module module, final ModifiableRootModel rootModel, String sdkPath, @Nullable PersistenceApi persistenceApi) {
    super.addSupport(module, rootModel, null, null);
    final AppEngineFacet appEngineFacet = FacetManager.getInstance(module).getFacetByType(AppEngineFacet.ID);
    LOG.assertTrue(appEngineFacet != null);
    final AppEngineFacetConfiguration facetConfiguration = appEngineFacet.getConfiguration();
    facetConfiguration.setSdkHomePath(sdkPath);
    final AppEngineSdk sdk = appEngineFacet.getSdk();
    final Artifact artifact = findContainingArtifact(module, appEngineFacet);

    final ApplicationServer appServer = sdk.getOrCreateAppServer();
    final Project project = module.getProject();
    if (appServer != null) {
      final ConfigurationFactory type = AppEngineServerConfigurationType.getInstance().getConfigurationFactories()[0];
      final RunnerAndConfigurationSettings settings = J2EEConfigurationFactory.getInstance().addAppServerConfiguration(project, type, appServer);
      if (artifact != null) {
        final CommonModel configuration = (CommonModel)settings.getConfiguration();
        ((AppEngineServerModel)configuration.getServerModel()).setArtifact(artifact);
        BuildArtifactsBeforeRunTaskProvider.setBuildArtifactBeforeRun(project, configuration, artifact);
      }
    }

    final Library apiJar = addProjectLibrary(module, "AppEngine API", sdk.getLibUserDirectoryPath(), VirtualFile.EMPTY_ARRAY);
    rootModel.addLibraryEntry(apiJar);
    if (artifact != null) {
      WebArtifactUtil.getInstance().addLibrary(apiJar, artifact, project);
    }

    if (persistenceApi != null) {
      facetConfiguration.setRunEnhancerOnMake(true);
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
        if (persistenceApi == PersistenceApi.JDO) {
          createFileFromTemplate(AppEngineTemplateGroupDescriptorFactory.APP_ENGINE_JDO_CONFIG_TEMPLATE, metaInf, AppEngineUtil.JDO_CONFIG_XML_NAME);
        }
        else {
          final VirtualFile file = createFileFromTemplate(AppEngineTemplateGroupDescriptorFactory.APP_ENGINE_JPA_CONFIG_TEMPLATE, metaInf, AppEngineUtil.JPA_CONFIG_XML_NAME);
          if (file != null) {
            JpaFacet facet = FacetManager.getInstance(module).getFacetByType(JpaFacet.ID);
            if (facet == null) {
              final JpaFacet jpaFacet = FacetManager.getInstance(module).addFacet(JpaFacetType.INSTANCE, JpaFacetType.INSTANCE.getDefaultFacetName(), null);
              jpaFacet.getDescriptorsContainer().getConfiguration().replaceConfigFile(JavaeePersistenceDescriptorsConstants.PERSISTENCE_XML_META_DATA, file.getUrl());
            }
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
      final Library library = addProjectLibrary(module, "AppEngine ORM", sdk.getOrmLibDirectoryPath(), sdk.getOrmLibSources());
      rootModel.addLibraryEntry(library);
      if (artifact != null) {
        WebArtifactUtil.getInstance().addLibrary(library, artifact, project);
      }
    }
  }

  @Nullable
  private static Artifact findContainingArtifact(Module module, AppEngineFacet appEngineFacet) {
    final PackagingElementResolvingContext context = ArtifactManager.getInstance(module.getProject()).getResolvingContext();
    final List<ArtifactType> artifactTypes =
      Collections.singletonList(WebArtifactUtil.getInstance().getExplodedWarArtifactType());
    final Collection<Artifact> artifacts = JavaeeArtifactUtil.getInstance().getArtifactsContainingFacet(appEngineFacet.getWebFacet(), context,
                                                                                                        artifactTypes, true);
    return ContainerUtil.getFirstItem(artifacts, null);
  }

  private static Library addProjectLibrary(final Module module, final String name, final String path, final VirtualFile[] sources) {
    return new WriteAction<Library>() {
      protected void run(final Result<Library> result) {
        final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(module.getProject());
        Library library = libraryTable.getLibraryByName(name);
        if (library == null) {
          library = libraryTable.createLibrary(name);
          final Library.ModifiableModel model = library.getModifiableModel();
          model.addJarDirectory(VfsUtil.pathToUrl(path), false);
          for (VirtualFile sourceRoot : sources) {
            model.addRoot(sourceRoot, OrderRootType.SOURCES);
          }
          model.commit();
        }
        result.setResult(library);
      }
    }.execute().getResultObject();
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
  public FrameworkSupportConfigurableBase createConfigurable(@NotNull FrameworkSupportModel model) {
    return new AppEngineSupportConfigurable(model);
  }

  private class AppEngineSupportConfigurable extends FrameworkSupportConfigurableBase implements FrameworkSupportModelListener {
    private JPanel myMainPanel;
    private AppEngineSdkEditor mySdkEditor;
    private JComboBox myPersistenceApiComboBox;
    private JPanel mySdkPanel;

    private AppEngineSupportConfigurable(FrameworkSupportModel model) {
      super(AppEngineSupportProvider.this);
      mySdkEditor = new AppEngineSdkEditor(null, true);
      mySdkPanel.add(LabeledComponent.create(mySdkEditor.getMainComponent(), "AppEngine SDK:"), BorderLayout.CENTER);
      PersistenceApiComboboxUtil.setComboboxModel(myPersistenceApiComboBox, true);
      if (model.isFrameworkSelected(JPA_PROVIDER_ID)) {
        myPersistenceApiComboBox.setSelectedItem(PersistenceApi.JPA.getName());
      }
      model.addFrameworkListener(this);
    }

    public void frameworkSelected(@NotNull FrameworkSupportProvider provider) {
      if (provider.getId().equals(JPA_PROVIDER_ID)) {
        myPersistenceApiComboBox.setSelectedItem(PersistenceApi.JPA.getName());
      }
    }

    public void frameworkUnselected(@NotNull FrameworkSupportProvider provider) {
      if (provider.getId().equals(JPA_PROVIDER_ID)) {
        myPersistenceApiComboBox.setSelectedItem(PersistenceApiComboboxUtil.NONE_ITEM);
      }
    }

    @Override
    public void addSupport(@NotNull Module module, @NotNull ModifiableRootModel rootModel, @Nullable Library library) {
      AppEngineSupportProvider.this.addSupport(module, rootModel, mySdkEditor.getPath(), PersistenceApiComboboxUtil.getSelectedApi(myPersistenceApiComboBox));
    }


    @Override
    public JComponent getComponent() {
      return myMainPanel;
    }

  }

}
