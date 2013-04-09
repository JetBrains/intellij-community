package com.intellij.appengine.facet.impl;

import com.intellij.appengine.facet.AppEngineWebIntegration;
import com.intellij.facet.FacetManager;
import com.intellij.javaee.JavaeePersistenceDescriptorsConstants;
import com.intellij.javaee.web.artifact.WebArtifactUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.jpa.facet.JpaFacet;
import com.intellij.jpa.facet.JpaFacetType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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

    final VirtualFile webXml = webFacet.getWebXmlDescriptor().getVirtualFile();
    if (webXml == null) return null;
    return webXml.getParent();
  }

  public void setupJpaSupport(Module module, VirtualFile persistenceXml) {
    JpaFacet facet = FacetManager.getInstance(module).getFacetByType(JpaFacet.ID);
    if (facet == null) {
      final JpaFacet jpaFacet = FacetManager.getInstance(module).addFacet(
        JpaFacetType.getInstance(), JpaFacetType.getInstance().getDefaultFacetName(), null);
      jpaFacet.getDescriptorsContainer().getConfiguration().replaceConfigFile(
        JavaeePersistenceDescriptorsConstants.PERSISTENCE_XML_META_DATA, persistenceXml.getUrl());
    }
  }
}
