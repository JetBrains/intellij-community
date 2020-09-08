// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.AddCustomLibraryDialog;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.GroovyLibraryDescription;

import java.util.Arrays;

/**
 * @author peter
 */
public class MvcUpgradeAction extends MvcActionBase {
  @Override
  protected void actionPerformed(@NotNull AnActionEvent e, @NotNull final Module module, @NotNull final MvcFramework framework) {
    final GroovyLibraryDescription description = framework.createLibraryDescription();
    final AddCustomLibraryDialog dialog =
      AddCustomLibraryDialog.createDialog(description, module, modifiableRootModel -> removeOldMvcSdk(framework, modifiableRootModel));
    dialog.setTitle(GroovyBundle.message("mvc.framework.0.change.sdk.version.title", framework.getDisplayName()));
    if (dialog.showAndGet()) {
      module.putUserData(MvcFramework.UPGRADE, Boolean.TRUE);
      module.putUserData(MvcModuleStructureUtil.LAST_MVC_VERSION, null);
    }
  }

  public static void removeOldMvcSdk(MvcFramework framework, ModifiableRootModel model) {
    final LibraryPresentationManager presentationManager = LibraryPresentationManager.getInstance();
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        final Library library = ((LibraryOrderEntry)entry).getLibrary();
        final LibrariesContainer container = LibrariesContainerFactory.createContainer(model);
        if (library != null) {
          final VirtualFile[] files = container.getLibraryFiles(library, OrderRootType.CLASSES);
          if (presentationManager.isLibraryOfKind(Arrays.asList(files), framework.getLibraryKind())) {
            model.removeOrderEntry(entry);
          }
        }
      }
    }
  }

  @Override
  protected void updateView(AnActionEvent event, @NotNull MvcFramework framework, @NotNull Module module) {
    event.getPresentation().setEnabledAndVisible(framework.isUpgradeActionSupported(module));
    event.getPresentation().setIcon(framework.getIcon());
  }
}

