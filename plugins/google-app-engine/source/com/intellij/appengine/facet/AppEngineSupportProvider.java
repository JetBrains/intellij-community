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
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.javaee.run.configuration.CommonStrategy;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

/**
 * @author nik
 */
public class AppEngineSupportProvider extends FacetTypeFrameworkSupportProvider<AppEngineFacet> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.facet.AppEngineSupportProvider");

  public AppEngineSupportProvider() {
    super(FacetTypeRegistry.getInstance().findFacetType(AppEngineFacet.ID));
  }

  protected void setupConfiguration(AppEngineFacet facet, ModifiableRootModel rootModel, String version) {
    final WebFacet webFacet = facet.getWebFacet();
    final VirtualFile webXml = webFacet.getWebXmlDescriptor().getVirtualFile();
    if (webXml == null) return;

    final FileTemplate template = FileTemplateManager.getInstance().getJ2eeTemplate(AppEngineTemplateGroupDescriptorFactory.APP_ENGINE_WEB_XML_TEMPLATE);
    try {
      final String text = template.getText(FileTemplateManager.getInstance().getDefaultProperties());
      final VirtualFile file = webXml.getParent().createChildData(this, AppEngineUtil.APPENGINE_WEB_XML_NAME);
      VfsUtil.saveText(file, text);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void addSupport(Module module, ModifiableRootModel rootModel, String sdkPath) {
    super.addSupport(module, rootModel, null, null);
    final AppEngineFacet appEngineFacet = FacetManager.getInstance(module).getFacetByType(AppEngineFacet.ID);
    LOG.assertTrue(appEngineFacet != null);
    appEngineFacet.getConfiguration().setSdkHomePath(sdkPath);
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
  }

  @NotNull
  @Override
  public VersionConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return new AppEngineSupportConfigurable();
  }

  private class AppEngineSupportConfigurable extends VersionConfigurable {
    private AppEngineSdkEditor mySdkEditor;

    private AppEngineSupportConfigurable() {
      super(AppEngineSupportProvider.this, ArrayUtil.EMPTY_STRING_ARRAY, null);
      mySdkEditor = new AppEngineSdkEditor(null);
    }

    @Override
    public void addSupport(Module module, ModifiableRootModel rootModel, @Nullable Library library) {
      AppEngineSupportProvider.this.addSupport(module, rootModel, mySdkEditor.getPath());
    }

    @Override
    public JComponent getComponent() {
      return mySdkEditor.getMainComponent();
    }

  }
}
