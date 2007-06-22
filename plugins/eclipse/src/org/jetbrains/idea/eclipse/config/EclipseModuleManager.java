package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class EclipseModuleManager implements ModuleComponent {
  private final Module myModule;

  private CachedXmlDocumentSet myDocumentSet;

  public static EclipseModuleManager getInstance(Module module) {
    return module.getComponent(EclipseModuleManager.class);
  }

  public EclipseModuleManager( Module module ) {
    myModule = module;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "EclipseModuleManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void moduleAdded() {
    if ( myModule.getProject().isInitialized()) {
      EclipseProjectResolver.resolveProjectReferences(myModule.getProject(), myModule, new HashSet<String>());

      final Set<String> unresolvedModules = new HashSet<String>();
      EclipseProjectResolver.resolveProjectReferences(myModule, null, unresolvedModules);
      EclipseProjectResolver.displayUnresolvedModules(myModule.getProject(), unresolvedModules);
    }
  }

  public CachedXmlDocumentSet getDocumentSet() {
    return myDocumentSet;
  }

  public void setDocumentSet(final CachedXmlDocumentSet documentSet) {
    myDocumentSet = documentSet;
  }
}
