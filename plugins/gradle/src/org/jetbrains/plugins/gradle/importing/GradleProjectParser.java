package org.jetbrains.plugins.gradle.importing;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Encapsulates functionality of performing <code>'build.gradle' -&gt; {@link GradleProject}</code> conversion.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/3/11 12:04 PM
 */
public class GradleProjectParser {

  /**
   * // TODO den add doc
   * 
   * @param file
   * @return
   * @throws IllegalArgumentException   if given file handle doesn't point to valid gradle project
   */
  @NotNull
  public GradleProject parse(@NotNull File file) throws IllegalArgumentException{
    if (!file.isFile()) {
      
    }
    // TODO den implement
    return new GradleProject(file);
  }
}
