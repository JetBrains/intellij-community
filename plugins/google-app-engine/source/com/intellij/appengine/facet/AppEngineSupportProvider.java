package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.server.instance.AppEngineServerModel;
import com.intellij.appengine.server.run.AppEngineServerConfigurationType;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.impl.ui.FacetTypeFrameworkSupportProvider;
import com.intellij.facet.impl.ui.VersionConfigurable;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportModel;
import com.intellij.javaee.JavaeePersistenceDescriptorsConstants;
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.javaee.module.JavaeePackagingConfiguration;
import com.intellij.javaee.run.configuration.CommonStrategy;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.jpa.facet.JpaFacet;
import com.intellij.jpa.facet.JpaFacetType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * @author nik
 */
public class AppEngineSupportProvider extends FacetTypeFrameworkSupportProvider<AppEngineFacet> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.facet.AppEngineSupportProvider");
  private static final String JPA_PROVIDER_ID = "facet:jpa";

  public AppEngineSupportProvider() {
    super(FacetTypeRegistry.getInstance().findFacetType(AppEngineFacet.ID));
  }

  @Override
  public String[] getPrecedingFrameworkProviderIds() {
    return new String[]{JPA_PROVIDER_ID};
  }

  protected void setupConfiguration(AppEngineFacet facet, ModifiableRootModel rootModel, String version) {
    final WebFacet webFacet = facet.getWebFacet();
    final VirtualFile webXml = webFacet.getWebXmlDescriptor().getVirtualFile();
    if (webXml == null) return;

    createFileFromTemplate(AppEngineTemplateGroupDescriptorFactory.APP_ENGINE_WEB_XML_TEMPLATE, webXml.getParent(), AppEngineUtil.APPENGINE_WEB_XML_NAME);
  }

  @Nullable
  private VirtualFile createFileFromTemplate(final String templateName, final VirtualFile parent, final String fileName) {
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
    final ApplicationServer appServer = sdk.getOrCreateAppServer();
    if (appServer != null) {
      final ConfigurationFactory type = AppEngineServerConfigurationType.getInstance().getConfigurationFactories()[0];
      final RunManagerEx runManager = RunManagerEx.getInstanceEx(module.getProject());
      final RunnerAndConfigurationSettingsImpl runSettings = (RunnerAndConfigurationSettingsImpl)runManager.createRunConfiguration("AppEngine Dev", type);

      final CommonStrategy configuration = (CommonStrategy)runSettings.getConfiguration();
      configuration.setApplicationServer(appServer);
      configuration.setUrlToOpenInBrowser(configuration.getDefaultUrlForBrowser());
      ((AppEngineServerModel)configuration.getServerModel()).setWebFacet(appEngineFacet.getWebFacet());

      runManager.addConfiguration(runSettings, false);
      runManager.setActiveConfiguration(runSettings);
    }

    final Library apiJar = addProjectLibrary(module, "AppEngine API", sdk.getLibUserDirectoryPath(), VirtualFile.EMPTY_ARRAY);
    rootModel.addLibraryEntry(apiJar);
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
      final JavaeePackagingConfiguration configuration = appEngineFacet.getWebFacet().getPackagingConfiguration();
      configuration.addLibraryLink(library);
      configuration.addLibraryLink(apiJar);
    }
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
  public VersionConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return new AppEngineSupportConfigurable();
  }

  private class AppEngineSupportConfigurable extends VersionConfigurable {
    private JPanel myMainPanel;
    private AppEngineSdkEditor mySdkEditor;
    private JComboBox myPersistenceApiComboBox;
    private JPanel myOptionsPanel;

    private AppEngineSupportConfigurable() {
      super(AppEngineSupportProvider.this, ArrayUtil.EMPTY_STRING_ARRAY, null);
      mySdkEditor = new AppEngineSdkEditor(null, true);
      myMainPanel = new JPanel(new BorderLayout());
      myMainPanel.add(mySdkEditor.getMainComponent(), BorderLayout.CENTER);
      myMainPanel.add(myOptionsPanel, BorderLayout.SOUTH);
      PersistenceApiComboboxUtil.setComboboxModel(myPersistenceApiComboBox, true);
    }

    @Override
    public void addSupport(Module module, ModifiableRootModel rootModel, @Nullable Library library) {
      AppEngineSupportProvider.this.addSupport(module, rootModel, mySdkEditor.getPath(), PersistenceApiComboboxUtil.getSelectedApi(myPersistenceApiComboBox));
    }

    @Override
    public JComponent getComponent() {
      return myMainPanel;
    }

  }

}
