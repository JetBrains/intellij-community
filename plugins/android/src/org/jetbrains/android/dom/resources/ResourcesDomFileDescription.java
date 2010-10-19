package org.jetbrains.android.dom.resources;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.android.resourceManagers.ResourceManager;

/**
 * @author yole
 */
public class ResourcesDomFileDescription extends DomFileDescription<Resources> {
  public ResourcesDomFileDescription() {
    super(Resources.class, "resources");
  }

  @Override
  public boolean isMyFile(@NotNull final XmlFile file, @Nullable Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return ResourceManager.isInResourceSubdirectory(file, "values");
      }
    });
  }
}
