package com.intellij.appengine.config;

import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.facet.impl.ui.FacetTypeFrameworkSupportProvider;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportConfigurable;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportModel;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportProvider;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.Collection;

/**
 * @author nik
 */
public class AppEngineSupportProvider extends FrameworkSupportProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.config.AppEngineSupportProvider");

  public AppEngineSupportProvider() {
    super("app-engine", "Google App Engine");
  }

  @Override
  public String getUnderlyingFrameworkId() {
    return FacetTypeFrameworkSupportProvider.getProviderId(WebFacet.ID);
  }

  @Override
  public boolean isSupportAlreadyAdded(@NotNull Module module) {
    final Collection<WebFacet> webFacets = WebFacet.getInstances(module);
    for (WebFacet webFacet : webFacets) {
      if (AppEngineUtil.isAppEngineSupportEnabled(webFacet)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public FrameworkSupportConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return new AppEngineSupportConfigurable();
  }

  private static class AppEngineSupportConfigurable extends FrameworkSupportConfigurable {
    @Override
    public JComponent getComponent() {
      return null;
    }

    @Override
    public void addSupport(Module module, ModifiableRootModel model, @Nullable Library library) {
      final Collection<WebFacet> webFacets = WebFacet.getInstances(module);
      if (webFacets.isEmpty()) {
        return;
      }

      final WebFacet webFacet = webFacets.iterator().next();
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
  }
}
