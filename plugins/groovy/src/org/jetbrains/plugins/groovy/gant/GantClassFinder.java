package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;

import java.util.List;

/**
 * @author peter
 */
public class GantClassFinder extends NonClasspathClassFinder {
  private final GantSettings mySettings;

  public GantClassFinder(GantSettings settings, Project project) {
    super(project);
    mySettings = settings;
  }

  @Override
  protected List<VirtualFile> getClassRoots() {
    return mySettings.getClassRoots();
  }

}
