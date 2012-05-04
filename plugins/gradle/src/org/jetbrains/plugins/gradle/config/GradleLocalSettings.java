package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds local project-level gradle-related settings (should be kept at the '*.iws' or 'workspace.xml').
 * 
 * @author Denis Zhdanov
 * @since 5/3/12 6:16 PM
 */
@State(name = "GradleLocalSettings", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)} )
public class GradleLocalSettings implements PersistentStateComponent<GradleLocalSettings> {

  @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
  private static final boolean PRESERVE_EXPAND_STATE = !Boolean.getBoolean("gradle.forget.expand.nodes.state");

  /** Holds changes confirmed by the end-user. */
  private final AtomicReference<Map<String/*tree path*/, Boolean/*expanded*/>> myExpandStates
    = new AtomicReference<Map<String, Boolean>>(new HashMap<String, Boolean>());
  /** @see #getWorkingExpandStates() */
  private final AtomicReference<Map<String/*tree path*/, Boolean/*expanded*/>> myWorkingExpandStates
    = new AtomicReference<Map<String, Boolean>>(new HashMap<String, Boolean>());

  @NotNull
  public static GradleLocalSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleLocalSettings.class);
  }
  
  @Override
  public GradleLocalSettings getState() {
    myExpandStates.get().clear();
    if (PRESERVE_EXPAND_STATE) {
      myExpandStates.get().putAll(myWorkingExpandStates.get());
    }
    return this;
  }

  @Override
  public void loadState(GradleLocalSettings state) {
    XmlSerializerUtil.copyBean(state, this); 
  }

  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public Map<String, Boolean> getExpandStates() { // Necessary for the serialization.
    return myExpandStates.get();
  }

  /**
   * It's possible to configure the gradle integration to not persist 'expand states' (see {@link #PRESERVE_EXPAND_STATE}).
   * <p/>
   * However, we want the state to be saved during the single IDE session even if we don't want to persist it between the
   * different sessions.
   * <p/>
   * This method allows to retrieve that 'non-persistent state'.
   *
   * @return    project structure changes tree nodes 'expand state' to use
   */
  @NotNull
  public Map<String, Boolean> getWorkingExpandStates() {
    return myWorkingExpandStates.get();
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setExpandStates(@Nullable Map<String, Boolean> state) { // Necessary for the serialization.
    if (state != null) {
      myExpandStates.get().putAll(state);
      myWorkingExpandStates.get().putAll(state);
    }
  }
}
