package org.jetbrains.plugins.gradle.importing;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Application-level representation of the <code>'build.gradle'</code> file.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 1:30 PM
 */
public class GradleProject {

  private final File myFile;
  
  /**
   * Creates new <code>GradleProject</code> object for the given gradle project file.
   * 
   * @param projectFile  target gradle project file
   */
  public GradleProject(@NotNull File projectFile) {
    myFile = projectFile;
  }

  /**
   * Parses underlying <code>'build.gradle'</code> file and populates current object within that information.
   */
  public void build() {
    // TODO den implement
  }
}
