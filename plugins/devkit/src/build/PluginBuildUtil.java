package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;

import java.util.Set;

/**
 * User: anna
 * Date: Nov 24, 2004
 */
public class PluginBuildUtil {

  public static String getPluginExPath(Module module){
    final ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
    if (! (jdk.getSdkType() instanceof IdeaJdk)){
      return null;
    }
    return ((Sandbox)jdk.getSdkAdditionalData()).getSandboxHome() + "/plugins/" + module.getName();
  }

  public static void getDependencies(Module module, Set<Module> modules) {
      if (modules.contains(module)) return;
      Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
      for (int i = 0; i < dependencies.length; i++) {
        Module dependency = dependencies[i];
        if (dependency.getModuleType() == ModuleType.JAVA) {
          modules.add(dependency);
          getDependencies(dependency, modules);
        }
      }
    }

    public static void getLibraries(Module module, Set<Library> libs) {
      OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (int i = 0; i < orderEntries.length; i++) {
        OrderEntry orderEntry = orderEntries[i];
        if (orderEntry instanceof LibraryOrderEntry) {
          LibraryOrderEntry libEntry = (LibraryOrderEntry)orderEntry;
          Library lib = libEntry.getLibrary();
          if (lib == null) continue;
          libs.add(lib);
        }
      }
    }
}
