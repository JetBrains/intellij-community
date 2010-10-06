package com.intellij.appengine.enhancement;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.generic.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class EnhancerCompiler extends GenericCompiler<String, VirtualFileWithDependenciesState, DummyPersistentState> {
  public EnhancerCompiler(Project project) {
    super("appengine-enhancer", 0, CompileOrderPlace.CLASS_POST_PROCESSING);
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getItemKeyDescriptor() {
    return STRING_KEY_DESCRIPTOR;
  }

  @NotNull
  @Override
  public DataExternalizer<VirtualFileWithDependenciesState> getSourceStateExternalizer() {
    return VirtualFileWithDependenciesState.EXTERNALIZER;
  }

  @NotNull
  @Override
  public DataExternalizer<DummyPersistentState> getOutputStateExternalizer() {
    return DummyPersistentState.EXTERNALIZER;
  }

  @NotNull
  @Override
  public GenericCompilerInstance<?, ? extends CompileItem<String, VirtualFileWithDependenciesState, DummyPersistentState>, String, VirtualFileWithDependenciesState, DummyPersistentState> createInstance(
    @NotNull CompileContext context) {
    return new EnhancerCompilerInstance(context);
  }

  @NotNull
  public String getDescription() {
    return "Google App Engine Enhancer";
  }
}
