package com.intellij.appengine.enhancement;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.openapi.compiler.generic.CompileItem;
import com.intellij.openapi.compiler.generic.DummyPersistentState;
import com.intellij.openapi.compiler.generic.VirtualFileWithDependenciesState;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class ClassFileItem extends CompileItem<String, VirtualFileWithDependenciesState, DummyPersistentState> {
  private final VirtualFile myClassFile;
  private final VirtualFile mySourceFile;
  private final List<VirtualFile> myDependencies;

  public ClassFileItem(VirtualFile classFile, VirtualFile sourceFile, List<VirtualFile> dependencies) {
    myClassFile = classFile;
    mySourceFile = sourceFile;
    myDependencies = dependencies;
  }

  @NotNull
  @Override
  public String getKey() {
    return myClassFile.getUrl();
  }

  @Override
  public boolean isSourceUpToDate(@NotNull VirtualFileWithDependenciesState state) {
    return state.isUpToDate(myClassFile);
  }

  @NotNull
  @Override
  public VirtualFileWithDependenciesState computeSourceState() {
    final VirtualFileWithDependenciesState state = new VirtualFileWithDependenciesState(myClassFile.getTimeStamp());
    for (VirtualFile dependency : myDependencies) {
      state.addDependency(dependency);
    }
    return state;
  }

  @Override
  public boolean isOutputUpToDate(@NotNull DummyPersistentState dummyPersistentState) {
    return true;
  }

  @NotNull
  @Override
  public DummyPersistentState computeOutputState() {
    return DummyPersistentState.INSTANCE;
  }

  @NotNull
  public VirtualFile getFile() {
    return myClassFile;
  }

  public VirtualFile getSourceFile() {
    return mySourceFile;
  }
}
