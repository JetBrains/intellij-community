package com.intellij.appengine.facet.impl;

import com.intellij.appengine.facet.AppEngineWebIntegration;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.server.instance.AppEngineServerModel;
import com.intellij.appengine.server.integration.AppEngineServerData;
import com.intellij.appengine.server.integration.AppEngineServerIntegration;
import com.intellij.appengine.server.run.AppEngineServerConfigurationType;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.facet.FacetManager;
import com.intellij.javaee.JavaeePersistenceDescriptorsConstants;
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.javaee.run.configuration.J2EEConfigurationFactory;
import com.intellij.javaee.serverInstances.ApplicationServersManager;
import com.intellij.javaee.web.artifact.WebArtifactUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.jpa.facet.JpaFacet;
import com.intellij.jpa.facet.JpaFacetType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.impl.run.BuildArtifactsBeforeRunTaskProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.descriptors.ConfigFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineUltimateWebIntegration extends AppEngineWebIntegration {
  @NotNull
  @Override
  public ArtifactType getAppEngineTargetArtifactType() {
    return WebArtifactUtil.getInstance().getExplodedWarArtifactType();
  }

  public VirtualFile suggestParentDirectoryForAppEngineWebXml(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
    final WebFacet webFacet = ContainerUtil.getFirstItem(WebFacet.getInstances(module));
    if (webFacet == null) return null;

    ConfigFile configFile = webFacet.getWebXmlDescriptor();
    if (configFile == null) return null;

    final VirtualFile webXml = configFile.getVirtualFile();
    if (webXml == null) return null;

    return webXml.getParent();
  }

  public void setupJpaSupport(@NotNull Module module, @NotNull VirtualFile persistenceXml) {
    JpaFacet facet = FacetManager.getInstance(module).getFacetByType(JpaFacet.ID);
    if (facet == null) {
      final JpaFacet jpaFacet = FacetManager.getInstance(module).addFacet(
        JpaFacetType.getInstance(), JpaFacetType.getInstance().getDefaultFacetName(), null);
      jpaFacet.getDescriptorsContainer().getConfiguration().replaceConfigFile(
        JavaeePersistenceDescriptorsConstants.PERSISTENCE_XML_META_DATA, persistenceXml.getUrl());
    }
  }

  public void setupRunConfiguration(@NotNull ModifiableRootModel rootModel, @NotNull AppEngineSdk sdk, Artifact artifact, @NotNull Project project) {
    final ApplicationServer appServer = getOrCreateAppServer(sdk);
    if (appServer != null) {
      final ConfigurationFactory type = AppEngineServerConfigurationType.getInstance().getConfigurationFactories()[0];
      final RunnerAndConfigurationSettings settings = J2EEConfigurationFactory.getInstance().addAppServerConfiguration(project, type, appServer);
      if (artifact != null) {
        final CommonModel configuration = (CommonModel)settings.getConfiguration();
        ((AppEngineServerModel)configuration.getServerModel()).setArtifact(artifact);
        BuildArtifactsBeforeRunTaskProvider.setBuildArtifactBeforeRun(project, configuration, artifact);
      }
      rootModel.addLibraryEntry(appServer.getLibrary()).setScope(DependencyScope.PROVIDED);
    }
  }

  @Override
  public void addLibraryToArtifact(@NotNull Library library, @NotNull Artifact artifact, @NotNull Project project) {
    WebArtifactUtil.getInstance().addLibrary(library, artifact, project);
  }

  public void setupDevServer(@NotNull final AppEngineSdk sdk) {
    getOrCreateAppServer(sdk);
  }

  private static ApplicationServer getOrCreateAppServer(AppEngineSdk sdk) {
    if (!sdk.isValid()) return null;
    final ApplicationServersManager serversManager = ApplicationServersManager.getInstance();
    final AppEngineServerIntegration integration = AppEngineServerIntegration.getInstance();

    final List<ApplicationServer> servers = serversManager.getApplicationServers(integration);
    File sdkHomeFile = new File(sdk.getSdkHomePath());
    for (ApplicationServer server : servers) {
      final String path = ((AppEngineServerData)server.getPersistentData()).getSdkPath();
      if (FileUtil.filesEqual(sdkHomeFile, new File(path))) {
        return server;
      }
    }

    return ApplicationServersManager.getInstance().createServer(integration, new AppEngineServerData(sdk.getSdkHomePath()));
  }

  public List<? extends AppEngineSdk> getSdkForConfiguredDevServers() {
    final List<ApplicationServer> servers = ApplicationServersManager.getInstance().getApplicationServers(AppEngineServerIntegration.getInstance());
    List<AppEngineSdk> sdkList = new ArrayList<AppEngineSdk>();
    for (ApplicationServer server : servers) {
      final AppEngineSdk sdk = ((AppEngineServerData)server.getPersistentData()).getSdk();
      if (sdk.isValid()) {
        sdkList.add(sdk);
      }
    }
    return sdkList;
  }
}
