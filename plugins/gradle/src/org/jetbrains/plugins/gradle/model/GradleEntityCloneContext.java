package org.jetbrains.plugins.gradle.model;

import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * We need to be able to perform cloning of gradle entities. However, there is a possible case that particular entity
 * encapsulates graph of other entities. For example, {@link GradleModule} has a number of
 * {@link GradleModule#getDependencies() dependencies} where those dependencies can reference other modules that, in turn, also
 * have dependencies.
 * <p/>
 * The problem is that we need to ensure that particular entity is shared within a single entities graph (e.g. there should
 * be a single shared instance of {@link GradleModule gradle module} after cloning). That's why we need some place to serve
 * as a cache during cloning. This class serves that purpose.
 * 
 * @author Denis Zhdanov
 * @since 9/28/11 12:36 PM
 */
public class GradleEntityCloneContext {
  
  private final Map<GradleLibrary, GradleLibrary> myLibraries = new HashMap<GradleLibrary, GradleLibrary>();
  private final Map<GradleModule, GradleModule> myModules = new HashMap<GradleModule, GradleModule>();

  @Nullable
  public GradleLibrary getLibrary(@NotNull GradleLibrary library) {
    return myLibraries.get(library);
  }

  public void store(@NotNull GradleLibrary key, @NotNull GradleLibrary value) {
    myLibraries.put(key, value);
  }
  
  @Nullable
  public GradleModule getModule(@NotNull GradleModule module) {
    return myModules.get(module);
  }

  public void store(@NotNull GradleModule key, @NotNull GradleModule value) {
    myModules.put(key, value);
  }
}
